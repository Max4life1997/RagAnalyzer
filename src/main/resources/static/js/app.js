// ─── Настройка marked + Prism.js ─────────────────────────────────────────────

// Отключаем автозапуск Prism при загрузке страницы — будем вызывать вручную
window.Prism = window.Prism || {};
Prism.manual = true;

const markedRenderer = new marked.Renderer();

// Кастомный рендерер блоков кода — оборачиваем в контейнер с кнопкой копирования
markedRenderer.code = (code, lang) => {
    const langClass = lang ? `language-${lang}` : "language-plaintext";
    const escaped = code
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;");

    return `
        <div class="code-block">
            <div class="code-block__header">
                <span class="code-block__lang">${lang || "code"}</span>
                <button class="code-block__copy" onclick="copyCode(this)">Копировать</button>
            </div>
            <pre class="${langClass}"><code class="${langClass}">${escaped}</code></pre>
        </div>`;
};

marked.setOptions({ renderer: markedRenderer, breaks: true, gfm: true });

// ─── Константы ───────────────────────────────────────────────────────────────

const STATUS = {
    INDEXED: "INDEXED",
    PROCESSING: "PROCESSING",
    FAILED: "FAILED"
};

// ─── API ─────────────────────────────────────────────────────────────────────

const api = {
    async getDocuments() {
        const res = await fetch("/api/documents");
        if (!res.ok) throw new Error(`Ошибка загрузки документов: ${res.status}`);
        return res.json();
    },

    async ask(question, documentIds, history) {
        const res = await fetch("/api/chat/ask", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ question, documentIds, history })
        });
        if (!res.ok) throw new Error(`Ошибка запроса: ${res.status}`);
        return res.json();
    },

    async uploadDocument(file, onProgress) {
        return new Promise((resolve, reject) => {
            const formData = new FormData();
            formData.append("file", file);

            const xhr = new XMLHttpRequest();
            xhr.open("POST", "/api/documents/upload");

            xhr.upload.addEventListener("progress", (e) => {
                if (e.lengthComputable) onProgress(Math.round((e.loaded / e.total) * 100));
            });

            xhr.addEventListener("load", () => {
                if (xhr.status >= 200 && xhr.status < 300) {
                    resolve(JSON.parse(xhr.responseText));
                } else {
                    reject(new Error(`Ошибка загрузки: ${xhr.status}`));
                }
            });

            xhr.addEventListener("error", () => reject(new Error("Сетевая ошибка")));
            xhr.send(formData);
        });
    },

    async deleteDocument(documentId) {
        const res = await fetch(`/api/documents/${documentId}`, { method: "DELETE" });
        if (!res.ok) throw new Error(`Ошибка удаления: ${res.status}`);
    }
};

// ─── Состояние ────────────────────────────────────────────────────────────────

const state = {
    documents: [],
    selectedDocumentIds: [],
    messages: []
};

// ─── DOM-узлы ────────────────────────────────────────────────────────────────

const els = {
    documentsTableBody:   document.getElementById("documentsTableBody"),
    selectedCount:        document.getElementById("selectedCount"),
    startChatBtn:         document.getElementById("startChatBtn"),
    clearSelectionBtn:    document.getElementById("clearSelectionBtn"),
    refreshDocumentsBtn:  document.getElementById("refreshDocumentsBtn"),
    searchInput:          document.getElementById("searchInput"),
    selectedDocumentsList:document.getElementById("selectedDocumentsList"),
    chatMessages:         document.getElementById("chatMessages"),
    questionInput:        document.getElementById("questionInput"),
    sendQuestionBtn:      document.getElementById("sendQuestionBtn"),
    changeDocumentsBtn:   document.getElementById("changeDocumentsBtn"),
    dropzone:             document.getElementById("dropzone"),
    fileInput:            document.getElementById("fileInput"),
    uploadStatus:         document.getElementById("uploadStatus")
};

// ─── Утилиты ─────────────────────────────────────────────────────────────────

function escapeHtml(text) {
    if (text == null) return "";
    return String(text)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
}

function formatDate(value) {
    if (!value) return "—";
    return new Date(value).toLocaleString("ru-RU");
}

function debounce(fn, ms) {
    let timer;
    return (...args) => {
        clearTimeout(timer);
        timer = setTimeout(() => fn(...args), ms);
    };
}

// ─── Рендеринг ───────────────────────────────────────────────────────────────

function renderStatusBadge(status) {
    const map = {
        [STATUS.INDEXED]:    ["status-badge--indexed",    "INDEXED"],
        [STATUS.PROCESSING]: ["status-badge--processing", "PROCESSING"],
    };
    const [cls, label] = map[status] ?? ["status-badge--failed", "FAILED"];
    return `<span class="status-badge ${escapeHtml(cls)}">${label}</span>`;
}

function renderDocuments() {
    const filter = els.searchInput.value.trim().toLowerCase();

    const filtered = state.documents.filter(doc =>
        doc.fileName.toLowerCase().includes(filter)
    );

    els.documentsTableBody.innerHTML = "";

    filtered.forEach(doc => {
        const checked  = state.selectedDocumentIds.includes(doc.id);
        const disabled = doc.status !== STATUS.INDEXED;

        const row = document.createElement("tr");
        row.innerHTML = `
            <td>
                <input type="checkbox" ${checked ? "checked" : ""} ${disabled ? "disabled" : ""} />
            </td>
            <td>
                <div class="doc-name">${escapeHtml(doc.fileName)}</div>
                <div class="doc-meta">${escapeHtml(doc.storedPath)}</div>
            </td>
            <td>${renderStatusBadge(doc.status)}</td>
            <td>${escapeHtml(doc.sizeBytes)} Б</td>
            <td>${escapeHtml(doc.chunksCount ?? 0)}</td>
            <td>${formatDate(doc.createdAt)}</td>
            <td>
                <button class="btn-delete" title="Удалить документ">
                    <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                        <path d="M2 3.5h10M5.5 3.5V2.5h3v1M5 3.5l.5 8M9 3.5l-.5 8" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
                    </svg>
                </button>
            </td>
        `;

        row.querySelector('input[type="checkbox"]')
            ?.addEventListener("change", () => toggleDocumentSelection(doc.id));

        row.querySelector(".btn-delete")
            ?.addEventListener("click", () => handleDeleteDocument(doc.id, doc.fileName));

        els.documentsTableBody.appendChild(row);
    });

    updateSelectionInfo();
}

function renderSelectedDocuments() {
    els.selectedDocumentsList.innerHTML = "";

    const selectedDocs = state.documents.filter(doc =>
        state.selectedDocumentIds.includes(doc.id)
    );

    if (selectedDocs.length === 0) {
        els.selectedDocumentsList.innerHTML =
            `<div class="doc-meta">Нет выбранных документов</div>`;
        return;
    }

    selectedDocs.forEach(doc => {
        const card = document.createElement("div");
        card.className = "selected-doc-card";
        card.innerHTML = `
            <div class="selected-doc-card__name">${escapeHtml(doc.fileName)}</div>
            <div class="selected-doc-card__meta">
                Статус: ${escapeHtml(doc.status)} • Чанков: ${escapeHtml(doc.chunksCount ?? 0)}
            </div>
        `;
        els.selectedDocumentsList.appendChild(card);
    });
}

function addMessage({ role, text, sources = [], images = [] }) {
    state.messages.push({ role, text, sources, images });

    const el = document.createElement("div");
    el.className = `message message--${role}`;

    const isUser = role === "user";
    const avatarLabel = isUser ? "Вы" : "R";
    const roleLabel   = isUser ? "Вы" : "RagAnalyzer";

    let sourcesHtml = "";
    if (sources.length > 0) {
        const seen = new Set();
        const uniqueSources = sources.filter(s => {
            const key = `${s.fileName}:${s.pageNumber}`;
            if (seen.has(key)) return false;
            seen.add(key);
            return true;
        });
        const chips = uniqueSources.map(s =>
            `<span class="source-chip">${escapeHtml(s.fileName)} · стр. ${escapeHtml(s.pageNumber)}</span>`
        ).join("");
        sourcesHtml = `
            <div class="message__sources">
                <div class="message__sources-title">Источники</div>
                ${chips}
            </div>`;
    }

    const textHtml = isUser
        ? `<div class="message__text">${escapeHtml(text)}</div>`
        : `<div class="message__text message__text--markdown">${marked.parse(text)}</div>`;

    let imagesHtml = "";
    if (!isUser && images.length > 0) {
        const imgTags = images.map(img =>
            `<div class="message__image-wrap">
                <img class="message__image" src="${escapeHtml(img.url)}" alt="Стр. ${escapeHtml(img.pageNumber)}" loading="lazy" />
                <div class="message__image-label">Стр. ${escapeHtml(img.pageNumber)}</div>
            </div>`
        ).join("");
        imagesHtml = `<div class="message__images">${imgTags}</div>`;
    }

    el.innerHTML = `
        <div class="message__avatar">${escapeHtml(avatarLabel)}</div>
        <div class="message__body">
            <div class="message__role">${escapeHtml(roleLabel)}</div>
            ${textHtml}
            ${imagesHtml}
            ${sourcesHtml}
        </div>
    `;

    // Подсвечиваем блоки кода внутри нового сообщения
    if (!isUser) Prism.highlightAllUnder(el);

    els.chatMessages.appendChild(el);
    els.chatMessages.scrollTop = els.chatMessages.scrollHeight;
}

function copyCode(btn) {
    const code = btn.closest(".code-block").querySelector("code").innerText;
    navigator.clipboard.writeText(code).then(() => {
        btn.textContent = "Скопировано!";
        btn.classList.add("code-block__copy--done");
        setTimeout(() => {
            btn.textContent = "Копировать";
            btn.classList.remove("code-block__copy--done");
        }, 2000);
    });
}

// ─── Логика выбора документов ────────────────────────────────────────────────

function updateSelectionInfo() {
    els.selectedCount.textContent = `Выбрано: ${state.selectedDocumentIds.length}`;
    els.startChatBtn.disabled = state.selectedDocumentIds.length === 0;
}

function toggleDocumentSelection(documentId) {
    const idx = state.selectedDocumentIds.indexOf(documentId);
    if (idx === -1) {
        state.selectedDocumentIds.push(documentId);
    } else {
        state.selectedDocumentIds.splice(idx, 1);
    }
    updateSelectionInfo();
    renderDocuments();
    renderSelectedDocuments();
}

function clearSelection() {
    state.selectedDocumentIds = [];
    updateSelectionInfo();
    renderDocuments();
    renderSelectedDocuments();
}

// ─── Навигация ────────────────────────────────────────────────────────────────

function switchView(viewId) {
    document.querySelectorAll(".view").forEach(v => v.classList.remove("view--active"));
    document.getElementById(viewId).classList.add("view--active");
}

function updateNav(viewId) {
    document.querySelectorAll(".nav-item").forEach(item => {
        item.classList.toggle("nav-item--active", item.dataset.view === viewId);
    });
}

// ─── Загрузка документов ──────────────────────────────────────────────────────

async function loadDocuments() {
    try {
        state.documents = await api.getDocuments();
        renderDocuments();
        renderSelectedDocuments();
    } catch (e) {
        console.error("Ошибка загрузки документов", e);
    }
}

// ─── Чат ──────────────────────────────────────────────────────────────────────

function startChat() {
    switchView("chatView");
    updateNav("chatView");
    renderSelectedDocuments();
}

function removeEmptyChat() {
    els.chatMessages.querySelector(".empty-chat")?.remove();
}

async function sendQuestion() {
    const question = els.questionInput.value.trim();

    if (!question) return;

    if (state.selectedDocumentIds.length === 0) {
        alert("Сначала выберите документы");
        return;
    }

    els.sendQuestionBtn.disabled = true;

    removeEmptyChat();
    addMessage({ role: "user", text: question });
    els.questionInput.value = "";

    try {
        // Передаём историю без последнего сообщения пользователя (оно уже добавлено выше)
        const history = state.messages.slice(0, -1).map(m => ({ role: m.role, text: m.text }));
        const data = await api.ask(question, state.selectedDocumentIds, history);
        addMessage({ role: "assistant", text: data.answer, sources: data.sources, images: data.images || [] });
    } catch (e) {
        console.error("Ошибка отправки вопроса", e);
        addMessage({ role: "assistant", text: "Произошла ошибка при получении ответа." });
    } finally {
        els.sendQuestionBtn.disabled = false;
    }
}

// ─── Навешивание событий ──────────────────────────────────────────────────────

document.querySelectorAll(".nav-item").forEach(button => {
    button.addEventListener("click", () => {
        const viewId = button.dataset.view;
        switchView(viewId);
        updateNav(viewId);
    });
});

els.refreshDocumentsBtn.addEventListener("click", loadDocuments);
els.clearSelectionBtn.addEventListener("click", clearSelection);
els.startChatBtn.addEventListener("click", startChat);
els.changeDocumentsBtn.addEventListener("click", () => {
    switchView("documentsView");
    updateNav("documentsView");
});
els.sendQuestionBtn.addEventListener("click", sendQuestion);
els.searchInput.addEventListener("input", debounce(renderDocuments, 200));
els.questionInput.addEventListener("keydown", (e) => {
    if (e.key === "Enter" && !e.shiftKey) {
        e.preventDefault();
        sendQuestion();
    }
});

// ─── Загрузка/удаление документов ────────────────────────────────────────────

function setUploadStatus(msg, type = "info") {
    els.uploadStatus.textContent = msg;
    els.uploadStatus.dataset.type = type;
    els.uploadStatus.hidden = !msg;
}

async function handleUploadFiles(files) {
    const allowed = [...files].filter(f =>
        f.name.endsWith(".pdf") || f.name.endsWith(".txt") || f.name.endsWith(".docx")
    );

    if (allowed.length === 0) {
        setUploadStatus("Поддерживаются только .pdf, .txt, .docx", "error");
        return;
    }

    for (const file of allowed) {
        setUploadStatus(`Загружается ${file.name}… 0%`, "info");
        try {
            await api.uploadDocument(file, (pct) => {
                setUploadStatus(`Загружается ${file.name}… ${pct}%`, "info");
            });
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

// ─── Сворачивание боковых панелей ─────────────────────────────────────────────

document.getElementById("navSidebarToggle").addEventListener("click", () => {
    document.getElementById("navSidebar").classList.toggle("collapsed");
});

document.getElementById("chatSidebarToggle").addEventListener("click", () => {
    document.getElementById("chatLayout").classList.toggle("chat-sidebar-collapsed");
});

// ─── Лайтбокс для изображений ────────────────────────────────────────────────

document.addEventListener("click", (e) => {
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

// Drag-and-drop
els.dropzone.addEventListener("click", () => els.fileInput.click());
els.fileInput.addEventListener("change", () => handleUploadFiles(els.fileInput.files));

els.dropzone.addEventListener("dragover", (e) => {
    e.preventDefault();
    els.dropzone.classList.add("dropzone--over");
});
els.dropzone.addEventListener("dragleave", () => els.dropzone.classList.remove("dropzone--over"));
els.dropzone.addEventListener("drop", (e) => {
    e.preventDefault();
    els.dropzone.classList.remove("dropzone--over");
    handleUploadFiles(e.dataTransfer.files);
});

// ─── Инициализация ────────────────────────────────────────────────────────────

loadDocuments();
