import { api } from "./api.js";
import { state, loadChats, saveChats, getActiveChat, createChat, tryGenerateTitle } from "./state.js";
import {
    debounce, escapeHtml,
    renderDocuments, renderChatsList, rerenderChatMessages,
    addMessageElement, createStreamingMessage, updateDocsBtnLabel
} from "./render.js";

// ─── DOM-узлы ────────────────────────────────────────────────────────────────

const els = {
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
};

// ─── Хелперы рендеринга (передаём els в функции из render.js) ────────────────

const refresh = {
    chatsList: () => renderChatsList(els, activateChat),
    documents: () => renderDocuments(els, toggleDocumentSelection, handleDeleteDocument),
    messages:  () => rerenderChatMessages(els),
    docsBtn:   () => updateDocsBtnLabel(els),
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
}

function startChat() {
    if (state.selectedDocumentIds.length === 0) { openDocsModal(); return; }
    const chat = createChat(state.selectedDocumentIds);
    state.activeChatId = chat.id;
    state.messages = chat.messages;
    refresh.chatsList();
    refresh.messages();
    refresh.docsBtn();
    closeDocsModal();
}

// ─── Документы ───────────────────────────────────────────────────────────────

function toggleDocumentSelection(documentId) {
    const idx = state.selectedDocumentIds.indexOf(documentId);
    if (idx === -1) state.selectedDocumentIds.push(documentId);
    else            state.selectedDocumentIds.splice(idx, 1);
    refresh.documents();
}

function clearSelection() {
    state.selectedDocumentIds = [];
    refresh.documents();
}

let _pollTimer = null;

async function loadDocuments() {
    try {
        state.documents = await api.getDocuments();
        refresh.documents();
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
    els.uploadStatus.hidden = !msg;
}

async function handleUploadFiles(files) {
    const allowed = [...files].filter(f =>
        f.name.endsWith(".pdf") || f.name.endsWith(".txt") || f.name.endsWith(".docx")
    );
    if (allowed.length === 0) { setUploadStatus("Поддерживаются только .pdf, .txt, .docx", "error"); return; }

    for (const file of allowed) {
        setUploadStatus(`Загружается ${file.name}… 0%`, "info");
        try {
            await api.uploadDocument(file, pct => setUploadStatus(`Загружается ${file.name}… ${pct}%`, "info"));
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

// ─── Модалка ─────────────────────────────────────────────────────────────────

function openDocsModal()  { loadDocuments(); els.docsModal.hidden = false; }
function closeDocsModal() { els.docsModal.hidden = true; }

// ─── Чат ─────────────────────────────────────────────────────────────────────

async function sendQuestion() {
    const question = els.questionInput.value.trim();
    if (!question) return;
    if (state.selectedDocumentIds.length === 0) { alert("Сначала выберите документы"); return; }

    els.sendQuestionBtn.disabled = true;
    els.chatMessages.querySelector(".empty-chat")?.remove();

    const chat = getActiveChat();
    const userMsg = { role: "user", text: question, sources: [], images: [] };
    if (chat) chat.messages.push(userMsg);
    state.messages = chat ? chat.messages : state.messages;
    addMessageElement(els, userMsg);
    els.questionInput.value = "";

    const streaming = createStreamingMessage(els);
    let sources = [], images = [], answerText = "";

    try {
        const history = state.messages.slice(0, -1).map(m => ({ role: m.role, text: m.text }));
        const response = await api.askStream(question, state.selectedDocumentIds, history);

        if (!response.ok) throw new Error(`Ошибка: ${response.status}`);

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = "";

        while (true) {
            const { done, value } = await reader.read();
            if (done) break;

            buffer += decoder.decode(value, { stream: true });
            const lines = buffer.split("\n");
            buffer = lines.pop(); // незавершённая строка остаётся в буфере

            for (const line of lines) {
                if (!line.startsWith("data:")) continue;
                const raw = line.slice(5).trim();
                try {
                    const event = JSON.parse(raw);
                    if (event.type === "meta") {
                        sources = event.sources || [];
                        images  = event.images  || [];
                    } else if (event.type === "token") {
                        streaming.appendToken(event.text);
                    } else if (event.type === "done") {
                        // Показываем изображения только если модель дала реальный ответ
                        const hasAnswer = streaming.getRawText().trim().length > 0;
                        answerText = streaming.finalize(sources, hasAnswer ? images : []);
                        if (!hasAnswer) images = [];
                    } else if (event.type === "error") {
                        streaming.finalize([], []);
                        answerText = event.text || "Произошла ошибка";
                        images = [];
                    }
                } catch (e) {
                    console.warn("SSE parse error:", raw, e);
                }
            }
        }

        if (!answerText) {
            streaming.appendToken("В загруженных документах нет информации для ответа на этот вопрос.");
            answerText = streaming.finalize([], []);
            images = [];
        }

        // Фильтруем изображения которые уже показывали в последних 10 обменах
        images = filterNewImages(images);

        console.log("[RAG] answer:", answerText, "| sources:", sources.length, "| images:", images.length);

        const reply = { role: "assistant", text: answerText, sources, images };
        if (chat) { chat.messages.push(reply); saveChats(); }
        tryGenerateTitle(refresh.chatsList);

    } catch (e) {
        console.error("Ошибка стриминга", e);
        streaming.finalize([], []);
    } finally {
        els.sendQuestionBtn.disabled = false;
    }
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
els.searchInput.addEventListener("input", debounce(refresh.documents, 200));
els.newChatBtn.addEventListener("click", openDocsModal);

// ─── Инициализация ────────────────────────────────────────────────────────────

loadChats();
refresh.chatsList();
refresh.docsBtn();
loadDocuments();

if (state.chats.length > 0) {
    activateChat(state.chats[0].id);
}
