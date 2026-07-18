import { api } from "./api.js";
import { getUser, logout } from "./auth.js";
import { state, loadChats, saveChats, getActiveChat, createChat, tryGenerateTitle } from "./state.js";
import {
    debounce, escapeHtml,
    renderDocuments, renderChatsList, rerenderChatMessages,
    addMessageElement, createStreamingMessage, updateDocsBtnLabel,
    renderFolderTree, renderFolderBreadcrumb, getDescendantFolderIds
} from "./render.js";

// ─── DOM-узлы ────────────────────────────────────────────────────────────────

const els = {
    logoutBtn:          document.getElementById("logoutBtn"),
    sidebarUserEmail:   document.getElementById("sidebarUserEmail"),
    chatMessages:       document.getElementById("chatMessages"),
    questionInput:      document.getElementById("questionInput"),
    sendQuestionBtn:    document.getElementById("sendQuestionBtn"),
    chatTitle:          document.getElementById("chatTitle"),
    docsBtn:            document.getElementById("docsBtn"),
    docsBtnLabel:       document.getElementById("docsBtnLabel"),
    chatsList:          document.getElementById("chatsList"),
    newChatBtn:         document.getElementById("newChatBtn"),
    docsModal:          document.getElementById("docsModal"),
    docsModalBackdrop:  document.getElementById("docsModalBackdrop"),
    docsModalClose:     document.getElementById("docsModalClose"),
    applyDocsBtn:       document.getElementById("applyDocsBtn"),
    documentsTableBody: document.getElementById("documentsTableBody"),
    selectedCount:      document.getElementById("selectedCount"),
    clearSelectionBtn:  document.getElementById("clearSelectionBtn"),
    searchInput:        document.getElementById("searchInput"),
    dropzone:           document.getElementById("dropzone"),
    fileInput:          document.getElementById("fileInput"),
    uploadStatus:       document.getElementById("uploadStatus"),
    adviceToggle:       document.getElementById("adviceToggle"),
    wikiToggleBtn:      document.getElementById("wikiToggleBtn"),
    chatSubtitle:       document.querySelector(".chat-header__subtitle"),
    folderTree:         document.getElementById("folderTree"),
    folderBreadcrumb:   document.getElementById("folderBreadcrumb"),
    newFolderBtn:       document.getElementById("newFolderBtn"),
    deleteSelectedBtn:  document.getElementById("deleteSelectedBtn"),
    deleteSelectedTreeBtn: document.getElementById("deleteSelectedTreeBtn"),
};

// ─── Хелперы рендеринга (передаём els в функции из render.js) ────────────────

const refresh = {
    chatsList: () => renderChatsList(els, activateChat),
    documents: () => {
        renderDocuments(els, toggleDocumentSelection, handleDeleteDocument, handleMoveDocument);
        const hasSelection = state.selectedDocumentIds.length > 0;
        els.deleteSelectedBtn.classList.toggle("is-hidden", !hasSelection);
        els.deleteSelectedBtn.textContent = hasSelection
            ? `Удалить выбранные (${state.selectedDocumentIds.length})`
            : "Удалить выбранные";
        els.deleteSelectedTreeBtn.classList.toggle("is-hidden", !hasSelection);
        els.deleteSelectedTreeBtn.title = hasSelection
            ? `Удалить выбранные (${state.selectedDocumentIds.length})`
            : "Удалить выбранные";
    },
    messages:  () => rerenderChatMessages(els),
    docsBtn:   () => updateDocsBtnLabel(els),
    wikiBtn:   () => updateWikiBtn(),
    folders:   () => {
        renderFolderTree(els, folderTreeHandlers);
        renderFolderBreadcrumb(els, navigateToFolder);
    },
};

function updateWikiBtn() {
    const chat = getActiveChat();
    const wikiOn = Boolean(chat?.wikiMode);

    els.wikiToggleBtn.classList.toggle("chat-header__wiki-btn--active", wikiOn);
    // В Wiki-режиме кнопка "Документы" остаётся видимой — можно сузить поиск до конкретных файлов
    els.docsBtn.style.display = "";

    if (els.chatSubtitle) {
        if (wikiOn) {
            els.chatSubtitle.textContent = state.selectedDocumentIds.length > 0
                ? "Wiki-режим: поиск по выбранным документам"
                : "Wiki-режим: поиск по всей вашей библиотеке документов";
        } else {
            els.chatSubtitle.textContent = "Выберите документы и начните диалог";
        }
    }

    refresh.docsBtn();
}

function toggleWikiMode() {
    const chat = getActiveChat();
    if (!chat) { openDocsModal(); return; }
    chat.wikiMode = !chat.wikiMode;
    saveChats();
    refresh.wikiBtn();
}

// ─── Папки ───────────────────────────────────────────────────────────────────

async function loadFolders() {
    try {
        state.folders = await api.getFolders();
        refresh.folders();
        refresh.documents();
    } catch (e) {
        console.error("Ошибка загрузки папок", e);
    }
}

function navigateToFolder(folderId) {
    state.currentFolderId = folderId;
    els.searchInput.value = "";
    refresh.folders();
    refresh.documents();
}

function toggleFolderExpand(folderId) {
    if (state.expandedFolderIds.has(folderId)) {
        state.expandedFolderIds.delete(folderId);
    } else {
        state.expandedFolderIds.add(folderId);
    }
    refresh.folders();
}

// Выбор/снятие выбора всех документов внутри папки (и подпапок) для текущего чата
function toggleFolderSelection(docIdsInFolder, currentlyChecked) {
    if (currentlyChecked) {
        state.selectedDocumentIds = state.selectedDocumentIds.filter(id => !docIdsInFolder.includes(id));
    } else {
        const toAdd = docIdsInFolder.filter(id => !state.selectedDocumentIds.includes(id));
        state.selectedDocumentIds = [...state.selectedDocumentIds, ...toAdd];
    }
    refresh.folders();
    refresh.documents();
}

async function createFolderPrompt() {
    const name = prompt("Название новой папки:");
    if (!name || !name.trim()) return;
    try {
        await api.createFolder(name.trim(), state.currentFolderId);
        await loadFolders();
    } catch (e) {
        alert(`Ошибка создания папки: ${e.message}`);
    }
}

async function renameFolderHandler(folderId, newName) {
    try {
        await api.renameFolder(folderId, newName);
        await loadFolders();
    } catch (e) {
        alert(`Ошибка переименования: ${e.message}`);
        await loadFolders();
    }
}

async function deleteFolderHandler(folderId, folderName) {
    if (!confirm(`Удалить папку «${folderName}»? Документы внутри переедут в корень.`)) return;
    try {
        await api.deleteFolder(folderId);
        if (state.currentFolderId === folderId) state.currentFolderId = null;
        await loadFolders();
        await loadDocuments();
    } catch (e) {
        alert(`Ошибка удаления папки: ${e.message}`);
    }
}

async function handleMoveDocument(documentId, folderId) {
    try {
        await api.moveDocument(documentId, folderId);
        await loadDocuments();
    } catch (e) {
        alert(`Ошибка перемещения: ${e.message}`);
        refresh.documents();
    }
}

const folderTreeHandlers = {
    onNavigate: navigateToFolder,
    onToggleExpand: toggleFolderExpand,
    onToggleFolderSelect: toggleFolderSelection,
    onToggleDocument: toggleDocumentSelection,
    onRename: renameFolderHandler,
    onDelete: deleteFolderHandler,
};

// ─── Управление чатами ───────────────────────────────────────────────────────

function activateChat(chatId) {
    state.activeChatId = chatId;
    const chat = getActiveChat();
    if (!chat) return;
    state.selectedDocumentIds = [...chat.documentIds];
    state.messages = chat.messages;
    refresh.chatsList();
    refresh.messages();
    refresh.docsBtn();
    refresh.wikiBtn();
}

function createNewChatFromButton() {
    const chat = createChat([]);
    activateChat(chat.id);
}

function startChat() {
    if (state.selectedDocumentIds.length === 0) { openDocsModal(); return; }
    const chat = createChat(state.selectedDocumentIds);
    state.activeChatId = chat.id;
    state.messages = chat.messages;
    refresh.chatsList();
    refresh.messages();
    refresh.docsBtn();
    refresh.wikiBtn();
    closeDocsModal();
}

// ─── Документы ───────────────────────────────────────────────────────────────

function toggleDocumentSelection(documentId) {
    const idx = state.selectedDocumentIds.indexOf(documentId);
    if (idx === -1) state.selectedDocumentIds.push(documentId);
    else            state.selectedDocumentIds.splice(idx, 1);
    refresh.documents();
    refresh.folders(); // чекбоксы папок зависят от выбора документов
}

function clearSelection() {
    state.selectedDocumentIds = [];
    refresh.documents();
    refresh.folders();
}

let _pollTimer = null;

async function loadDocuments() {
    try {
        state.documents = await api.getDocuments();
        refresh.documents();
        refresh.folders();
        scheduleStatusPoll();
    } catch (e) {
        console.error("Ошибка загрузки документов", e);
    }
}

// Автообновление: пока есть документы в статусе PROCESSING — опрашиваем каждые 2 сек
function scheduleStatusPoll() {
    clearTimeout(_pollTimer);
    const hasProcessing = state.documents.some(d => d.status === "PROCESSING" || d.status === "UPLOADING");
    if (!hasProcessing) return;

    _pollTimer = setTimeout(async () => {
        try {
            state.documents = await api.getDocuments();
            refresh.documents();
            scheduleStatusPoll();
        } catch (e) { /* тихо пропускаем */ }
    }, 2000);
}

function setUploadStatus(msg, type = "info") {
    els.uploadStatus.textContent = msg;
    els.uploadStatus.dataset.type = type;
    els.uploadStatus.classList.toggle("is-hidden", !msg);
}

async function handleUploadFiles(files) {
    const allowed = [...files].filter(f =>
        f.name.endsWith(".pdf") || f.name.endsWith(".txt") || f.name.endsWith(".docx")
    );
    if (allowed.length === 0) { setUploadStatus("Поддерживаются только .pdf, .txt, .docx", "error"); return; }

    for (const file of allowed) {
        setUploadStatus(`Загружается ${file.name}… 0%`, "info");
        try {
            await api.uploadDocument(
                file,
                pct => setUploadStatus(`Загружается ${file.name}… ${pct}%`, "info"),
                state.currentFolderId
            );
            setUploadStatus(`${file.name} — успешно загружен`, "success");
        } catch (e) {
            setUploadStatus(`Ошибка: ${e.message}`, "error");
        }
    }
    await loadDocuments();
    setTimeout(() => setUploadStatus(""), 3000);
}

async function handleDeleteDocument(documentId, fileName) {
    if (!confirm(`Удалить «${fileName}»?`)) return;
    try {
        await api.deleteDocument(documentId);
        state.selectedDocumentIds = state.selectedDocumentIds.filter(id => id !== documentId);
        await loadDocuments();
    } catch (e) {
        alert(`Ошибка удаления: ${e.message}`);
    }
}

async function handleDeleteSelected() {
    const ids = [...state.selectedDocumentIds];
    if (ids.length === 0) return;

    const word = ids.length === 1 ? "документ" : ids.length < 5 ? "документа" : "документов";
    if (!confirm(`Удалить ${ids.length} ${word}? Это действие необратимо.`)) return;

    els.deleteSelectedBtn.disabled = true;
    let failed = 0;

    for (const id of ids) {
        try {
            await api.deleteDocument(id);
        } catch (e) {
            failed++;
        }
    }

    state.selectedDocumentIds = [];
    await loadDocuments();
    els.deleteSelectedBtn.disabled = false;

    if (failed > 0) {
        alert(`Не удалось удалить ${failed} из ${ids.length} документов.`);
    }
}

// ─── Модалка ─────────────────────────────────────────────────────────────────

function openDocsModal()  { loadDocuments(); loadFolders(); els.docsModal.classList.remove("is-hidden"); }
function closeDocsModal() { els.docsModal.classList.add("is-hidden"); }

// ─── Чат ─────────────────────────────────────────────────────────────────────


function normalizeAnswerText(text) {
    if (text == null) return "";
    return String(text)
        .replace(/^\s*\[\s*$/gm, "")
        .replace(/^\s*\]\s*$/gm, "")
        .replace(/\\\[/g, "")
        .replace(/\\\]/g, "")
        .replace(/\\\(/g, "")
        .replace(/\\\)/g, "")
        .replace(/\\text\{([^}]*)\}/g, "$1")
        .replace(/\\frac\{([^{}]+)\}\{([^{}]+)\}/g, "($1) / ($2)")
        .replace(/\\cdot/g, "*")
        .replace(/\\times/g, "*")
        .replace(/\\approx/g, "~")
        .replace(/\\leq/g, "<=")
        .replace(/\\geq/g, ">=")
        .replace(/\\%/g, "%")
        .replace(/\\[a-zA-Z]+/g, "")
        .replace(/\{([^{}]*)\}/g, "$1")
        .replace(/\n{3,}/g, "\n\n")
        .trim();
}

// Обрезает текст на первом блоке из 3+ символов CJK (китайский/японский/корейский) —
// модель иногда продолжает генерацию и съезжает в другой язык после ответа
function cutAtForeignScript(text) {
    const match = text.match(/[一-鿿぀-ヿ가-힯]{3,}/);
    return match ? text.slice(0, match.index) : text;
}

function cleanStreamText(text) {
    return normalizeAnswerText(cutAtForeignScript((text || "")
        .replace(/<think>[\s\S]*?<\/think>/g, "")
        .trim()));
}

function filterNewImages(images, chat) {
    if (!chat || !Array.isArray(images) || images.length === 0) {
        return images || [];
    }

    const shownUrls = new Set(
        chat.messages
            .slice(-20)
            .flatMap(message => message.images || [])
            .map(image => image.url)
    );

    return images.filter(image => !shownUrls.has(image.url));
}

function refreshIfActive(chatId) {
    if (state.activeChatId === chatId) {
        refresh.messages();
    }
}

async function sendQuestion() {
    const question = els.questionInput.value.trim();
    if (!question) return;

    const chat = getActiveChat();
    if (!chat) { alert("\u0421\u043e\u0437\u0434\u0430\u0439\u0442\u0435 \u043d\u043e\u0432\u044b\u0439 \u0447\u0430\u0442"); return; }

    const wikiMode = Boolean(chat.wikiMode);

    if (!wikiMode && state.selectedDocumentIds.length === 0) {
        alert("\u0421\u043d\u0430\u0447\u0430\u043b\u0430 \u0432\u044b\u0431\u0435\u0440\u0438\u0442\u0435 \u0434\u043e\u043a\u0443\u043c\u0435\u043d\u0442\u044b \u0438\u043b\u0438 \u0432\u043a\u043b\u044e\u0447\u0438\u0442\u0435 Wiki-\u0440\u0435\u0436\u0438\u043c");
        return;
    }

    const chatId = chat.id;
    const documentIds = [...state.selectedDocumentIds];
    const adviceEnabled = Boolean(els.adviceToggle?.checked);
    const history = chat.messages.map(m => ({ role: m.role, text: m.text }));

    els.sendQuestionBtn.disabled = true;
    els.chatMessages.querySelector(".empty-chat")?.remove();

    const userMsg = { role: "user", text: question, sources: [], images: [] };
    const assistantMsg = { role: "assistant", text: "", sources: [], images: [], streaming: true };
    chat.messages.push(userMsg, assistantMsg);
    state.messages = chat.messages;
    saveChats();

    addMessageElement(els, userMsg);
    els.questionInput.value = "";

    const streaming = createStreamingMessage(els);
    let sources = [], images = [];

    try {
        const response = await api.askStream(question, documentIds, history, adviceEnabled, wikiMode);

        if (!response.ok) throw new Error(`\u041e\u0448\u0438\u0431\u043a\u0430: ${response.status}`);

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = "";

        while (true) {
            const { done, value } = await reader.read();
            if (done) break;

            buffer += decoder.decode(value, { stream: true });
            const lines = buffer.split("\n");
            buffer = lines.pop();

            for (const line of lines) {
                if (!line.startsWith("data:")) continue;
                const raw = line.slice(5).trim();
                try {
                    const event = JSON.parse(raw);
                    if (event.type === "meta") {
                        sources = event.sources || [];
                        images = event.images || [];
                    } else if (event.type === "token") {
                        assistantMsg.text += event.text || "";
                        if (state.activeChatId === chatId && streaming.isConnected()) {
                            streaming.appendToken(event.text || "");
                        }
                    } else if (event.type === "done") {
                        assistantMsg.text = cleanStreamText(assistantMsg.text);
                        const hasAnswer = assistantMsg.text.length > 0;
                        assistantMsg.sources = hasAnswer ? sources : [];
                        assistantMsg.images = hasAnswer ? filterNewImages(images, chat) : [];
                        assistantMsg.streaming = false;

                        if (state.activeChatId === chatId && streaming.isConnected()) {
                            streaming.finalize(assistantMsg.sources, assistantMsg.images);
                        } else {
                            refreshIfActive(chatId);
                        }
                    } else if (event.type === "error") {
                        assistantMsg.text = event.text || "\u041f\u0440\u043e\u0438\u0437\u043e\u0448\u043b\u0430 \u043e\u0448\u0438\u0431\u043a\u0430";
                        assistantMsg.sources = [];
                        assistantMsg.images = [];
                        assistantMsg.streaming = false;
                        if (state.activeChatId === chatId && streaming.isConnected()) {
                            streaming.finalize([], []);
                        }
                        refreshIfActive(chatId);
                    }
                } catch (e) {
                    console.warn("SSE parse error:", raw, e);
                }
            }
        }

        if (assistantMsg.streaming) {
            assistantMsg.text = cleanStreamText(assistantMsg.text)
                || "\u0412 \u0437\u0430\u0433\u0440\u0443\u0436\u0435\u043d\u043d\u044b\u0445 \u0434\u043e\u043a\u0443\u043c\u0435\u043d\u0442\u0430\u0445 \u043d\u0435\u0442 \u0438\u043d\u0444\u043e\u0440\u043c\u0430\u0446\u0438\u0438 \u0434\u043b\u044f \u043e\u0442\u0432\u0435\u0442\u0430 \u043d\u0430 \u044d\u0442\u043e\u0442 \u0432\u043e\u043f\u0440\u043e\u0441.";
            assistantMsg.sources = assistantMsg.text ? sources : [];
            assistantMsg.images = filterNewImages(images, chat);
            assistantMsg.streaming = false;
            if (state.activeChatId === chatId && streaming.isConnected()) {
                streaming.finalize(assistantMsg.sources, assistantMsg.images);
            } else {
                refreshIfActive(chatId);
            }
        }

        saveChats();
        if (state.activeChatId === chatId) {
            tryGenerateTitle(refresh.chatsList);
        }

    } catch (e) {
        console.error("\u041e\u0448\u0438\u0431\u043a\u0430 \u0441\u0442\u0440\u0438\u043c\u0438\u043d\u0433\u0430", e);
        assistantMsg.text = describeNetworkError(e);
        assistantMsg.sources = [];
        assistantMsg.images = [];
        assistantMsg.streaming = false;
        assistantMsg.failed = true;
        assistantMsg.retryQuestion = question;
        saveChats();
        if (state.activeChatId === chatId && streaming.isConnected()) {
            streaming.finalize([], []);
        } else {
            refreshIfActive(chatId);
        }
    } finally {
        els.sendQuestionBtn.disabled = false;
    }
}

// \u041f\u0435\u0440\u0435\u0432\u043e\u0434\u0438\u0442 \u0442\u0435\u0445\u043d\u0438\u0447\u0435\u0441\u043a\u0438\u0435 \u043e\u0448\u0438\u0431\u043a\u0438 \u0431\u0440\u0430\u0443\u0437\u0435\u0440\u0430 \u0432 \u043f\u043e\u043d\u044f\u0442\u043d\u044b\u0439 \u043f\u043e\u043b\u044c\u0437\u043e\u0432\u0430\u0442\u0435\u043b\u044e \u0442\u0435\u043a\u0441\u0442
function describeNetworkError(e) {
    const raw = (e && e.message || "").toLowerCase();
    if (raw.includes("network") || raw.includes("failed to fetch") || raw.includes("load failed")) {
        return "\u26a0\ufe0f \u0421\u043e\u0435\u0434\u0438\u043d\u0435\u043d\u0438\u0435 \u0441 \u0441\u0435\u0440\u0432\u0435\u0440\u043e\u043c \u043f\u0440\u0435\u0440\u0432\u0430\u043b\u043e\u0441\u044c \u0432\u043e \u0432\u0440\u0435\u043c\u044f \u043e\u0442\u0432\u0435\u0442\u0430. " +
               "\u041e\u0431\u044b\u0447\u043d\u043e \u044d\u0442\u043e \u0437\u043d\u0430\u0447\u0438\u0442, \u0447\u0442\u043e \u0441\u0435\u0440\u0432\u0435\u0440 \u043f\u0435\u0440\u0435\u0437\u0430\u043f\u0443\u0441\u043a\u0430\u0435\u0442\u0441\u044f. \u041f\u043e\u0434\u043e\u0436\u0434\u0438\u0442\u0435 \u043d\u0435\u043c\u043d\u043e\u0433\u043e \u0438 \u043f\u043e\u0432\u0442\u043e\u0440\u0438\u0442\u0435 \u0432\u043e\u043f\u0440\u043e\u0441.";
    }
    return "\u26a0\ufe0f \u041f\u0440\u043e\u0438\u0437\u043e\u0448\u043b\u0430 \u043e\u0448\u0438\u0431\u043a\u0430 \u043f\u0440\u0438 \u043f\u043e\u043b\u0443\u0447\u0435\u043d\u0438\u0438 \u043e\u0442\u0432\u0435\u0442\u0430: " + (e?.message || "\u043d\u0435\u0438\u0437\u0432\u0435\u0441\u0442\u043d\u0430\u044f \u043e\u0448\u0438\u0431\u043a\u0430");
}

// ─── События ─────────────────────────────────────────────────────────────────

els.sendQuestionBtn.addEventListener("click", sendQuestion);
els.questionInput.addEventListener("keydown", e => {
    if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); sendQuestion(); }
});

document.getElementById("navSidebarToggle").addEventListener("click", () => {
    document.getElementById("navSidebar").classList.toggle("collapsed");
});

document.addEventListener("click", e => {
    if (e.target.classList.contains("message__image")) {
        const lb = document.createElement("div");
        lb.className = "lightbox";
        const img = document.createElement("img");
        img.src = e.target.src;
        lb.appendChild(img);
        lb.addEventListener("click", () => lb.remove());
        document.body.appendChild(lb);
    }
});

els.dropzone.addEventListener("click", () => els.fileInput.click());
els.fileInput.addEventListener("change", () => handleUploadFiles(els.fileInput.files));
els.dropzone.addEventListener("dragover", e => { e.preventDefault(); els.dropzone.classList.add("dropzone--over"); });
els.dropzone.addEventListener("dragleave", () => els.dropzone.classList.remove("dropzone--over"));
els.dropzone.addEventListener("drop", e => {
    e.preventDefault();
    els.dropzone.classList.remove("dropzone--over");
    handleUploadFiles(e.dataTransfer.files);
});

els.docsBtn.addEventListener("click", openDocsModal);
els.docsModalClose.addEventListener("click", closeDocsModal);
els.docsModalBackdrop.addEventListener("click", closeDocsModal);
document.addEventListener("keydown", e => { if (e.key === "Escape") closeDocsModal(); });

els.applyDocsBtn.addEventListener("click", () => {
    const chat = getActiveChat();
    if (chat) {
        chat.documentIds = [...state.selectedDocumentIds];
        saveChats();
        refresh.docsBtn();
        closeDocsModal();
    } else {
        startChat();
    }
});

els.clearSelectionBtn.addEventListener("click", clearSelection);
els.deleteSelectedBtn.addEventListener("click", handleDeleteSelected);
els.deleteSelectedTreeBtn.addEventListener("click", handleDeleteSelected);
els.searchInput.addEventListener("input", debounce(refresh.documents, 200));
els.newChatBtn.addEventListener("click", createNewChatFromButton);
els.wikiToggleBtn.addEventListener("click", toggleWikiMode);
els.newFolderBtn.addEventListener("click", createFolderPrompt);

// ─── Кнопка выхода ───────────────────────────────────────────────────────────

if (els.logoutBtn) {
    els.logoutBtn.addEventListener("click", () => {
        if (confirm("Выйти из аккаунта?")) logout();
    });
}

// Показываем email текущего пользователя в сайдбаре
const currentUser = getUser();
if (currentUser?.email && els.sidebarUserEmail) {
    els.sidebarUserEmail.textContent = currentUser.email;
    // Первая буква в аватаре
    const avatar = document.querySelector(".sidebar__user-avatar");
    if (avatar) avatar.textContent = currentUser.email[0].toUpperCase();
}

// Ключ для localStorage чатов — изолируем по userId
const CHATS_KEY = currentUser?.userId ? `rag_chats_${currentUser.userId}` : "rag_chats";

// ─── Инициализация ────────────────────────────────────────────────────────────

loadChats(CHATS_KEY);
refresh.chatsList();
refresh.docsBtn();
refresh.wikiBtn();
loadDocuments();
// app.js больше не вызывает loadChats() повторно

if (state.chats.length > 0) {
    activateChat(state.chats[0].id);
}
