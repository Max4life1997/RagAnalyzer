import { state, STATUS, getActiveChat, renameChat, deleteChat, saveChats } from "./state.js";

// ─── Настройка marked + Prism ─────────────────────────────────────────────────

window.Prism = window.Prism || {};
Prism.manual = true;

const markedRenderer = new marked.Renderer();
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

// copyCode нужен глобально т.к. вызывается из onclick атрибута
window.copyCode = (btn) => {
    const code = btn.closest(".code-block").querySelector("code").innerText;
    navigator.clipboard.writeText(code).then(() => {
        btn.textContent = "Скопировано!";
        btn.classList.add("code-block__copy--done");
        setTimeout(() => {
            btn.textContent = "Копировать";
            btn.classList.remove("code-block__copy--done");
        }, 2000);
    });
};

// ─── Утилиты ─────────────────────────────────────────────────────────────────

export function escapeHtml(text) {
    if (text == null) return "";
    return String(text)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
}

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

function isNearBottom(container, threshold = 80) {
    return container.scrollHeight - container.scrollTop - container.clientHeight <= threshold;
}

function scrollToBottom(container) {
    container.scrollTop = container.scrollHeight;
}

function scrollToBottomIfNear(container, wasNearBottom) {
    if (wasNearBottom) {
        scrollToBottom(container);
    }
}

export function formatDate(value) {
    if (!value) return "—";
    return new Date(value).toLocaleString("ru-RU");
}

export function debounce(fn, ms) {
    let timer;
    return (...args) => {
        clearTimeout(timer);
        timer = setTimeout(() => fn(...args), ms);
    };
}

// ─── Рендеринг документов ────────────────────────────────────────────────────

export function renderStatusBadge(status) {
    const map = {
        [STATUS.INDEXED]:    ["status-badge--indexed",    "INDEXED"],
        [STATUS.PROCESSING]: ["status-badge--processing", "PROCESSING"],
    };
    const [cls, label] = map[status] ?? ["status-badge--failed", "FAILED"];
    return `<span class="status-badge ${escapeHtml(cls)}">${label}</span>`;
}

export function renderDocuments(els, onToggle, onDelete, onMove) {
    const filter = els.searchInput.value.trim().toLowerCase();

    // Внутри открытой папки показываем только её прямое содержимое.
    // Если идёт поиск — ищем по всей библиотеке, игнорируя текущую папку.
    const filtered = state.documents
        .filter(d => filter || (d.folderId || null) === state.currentFolderId)
        .filter(d => d.fileName.toLowerCase().includes(filter));

    els.documentsTableBody.innerHTML = "";

    filtered.forEach(doc => {
        const checked  = state.selectedDocumentIds.includes(doc.id);
        const disabled = doc.status !== STATUS.INDEXED;

        const moveOptions = `<option value="">Корень</option>` +
            state.folders.map(f =>
                `<option value="${f.id}" ${f.id === doc.folderId ? "selected" : ""}>${escapeHtml(f.name)}</option>`
            ).join("");

        const row = document.createElement("tr");
        row.innerHTML = `
            <td><input type="checkbox" ${checked ? "checked" : ""} ${disabled ? "disabled" : ""} /></td>
            <td>
                <div class="doc-name">${escapeHtml(doc.fileName)}</div>
                <div class="doc-meta">${escapeHtml(doc.storedPath)}</div>
            </td>
            <td>${renderStatusBadge(doc.status)}</td>
            <td>${escapeHtml(doc.chunksCount ?? 0)}</td>
            <td>${formatDate(doc.createdAt)}</td>
            <td><select class="doc-move-select" title="Переместить в папку">${moveOptions}</select></td>
            <td>
                <button class="btn-delete" title="Удалить документ">
                    <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                        <path d="M2 3.5h10M5.5 3.5V2.5h3v1M5 3.5l.5 8M9 3.5l-.5 8"
                              stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
                    </svg>
                </button>
            </td>`;

        row.querySelector('input[type="checkbox"]')?.addEventListener("change", () => onToggle(doc.id));
        row.querySelector(".btn-delete")?.addEventListener("click", () => onDelete(doc.id, doc.fileName));
        row.querySelector(".doc-move-select")?.addEventListener("change", (e) => onMove(doc.id, e.target.value || null));
        els.documentsTableBody.appendChild(row);
    });

    els.selectedCount.textContent = `Выбрано: ${state.selectedDocumentIds.length}`;
}

// ─── Дерево папок ────────────────────────────────────────────────────────────

export function getDescendantFolderIds(folderId, folders) {
    const result = [folderId];
    folders.filter(f => f.parentFolderId === folderId)
        .forEach(child => result.push(...getDescendantFolderIds(child.id, folders)));
    return result;
}

export function renderFolderBreadcrumb(els, onNavigate) {
    const { folders, currentFolderId } = state;
    els.folderBreadcrumb.innerHTML = "";

    const path = [];
    let id = currentFolderId;
    while (id) {
        const folder = folders.find(f => f.id === id);
        if (!folder) break;
        path.unshift(folder);
        id = folder.parentFolderId;
    }

    const rootItem = document.createElement("span");
    rootItem.className = "docs-modal__breadcrumb-item" + (path.length === 0 ? " docs-modal__breadcrumb-item--current" : "");
    rootItem.textContent = "Корень";
    rootItem.addEventListener("click", () => onNavigate(null));
    els.folderBreadcrumb.appendChild(rootItem);

    path.forEach((folder, idx) => {
        const sep = document.createElement("span");
        sep.className = "docs-modal__breadcrumb-sep";
        sep.textContent = "/";
        els.folderBreadcrumb.appendChild(sep);

        const isCurrent = idx === path.length - 1;
        const item = document.createElement("span");
        item.className = "docs-modal__breadcrumb-item" + (isCurrent ? " docs-modal__breadcrumb-item--current" : "");
        item.textContent = folder.name;
        if (!isCurrent) item.addEventListener("click", () => onNavigate(folder.id));
        els.folderBreadcrumb.appendChild(item);
    });
}

const FOLDER_ICON_SVG = `<svg width="14" height="14" viewBox="0 0 16 16" fill="none"><path d="M2 4a1 1 0 0 1 1-1h3l1.5 1.5H13a1 1 0 0 1 1 1V12a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V4z" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round"/></svg>`;
const CHEVRON_SVG = `<svg width="10" height="10" viewBox="0 0 10 10" fill="none"><path d="M3 1l4 4-4 4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>`;
const TRASH_SVG = `<svg width="12" height="12" viewBox="0 0 14 14" fill="none"><path d="M2 3.5h10M5.5 3.5V2.5h3v1M5 3.5l.5 8M9 3.5l-.5 8" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/></svg>`;
const FILE_ICON_SVG = `<svg width="13" height="13" viewBox="0 0 16 16" fill="none"><path d="M4 1.5h5l3 3v9a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-11a1 1 0 0 1 1-1z" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round"/><path d="M9 1.5v3h3" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round"/></svg>`;

// Файл-лист внутри дерева папок (тот же отступ что у папки, но без раскрытия/переименования)
function buildFileRow(doc, onToggleDocument) {
    const row = document.createElement("div");
    const disabled = doc.status !== STATUS.INDEXED;
    row.className = "folder-node__row file-node__row" + (disabled ? " file-node__row--disabled" : "");
    row.title = doc.fileName;

    const spacer = document.createElement("span");
    spacer.className = "folder-node__toggle folder-node__toggle--empty";
    row.appendChild(spacer);

    const checkbox = document.createElement("input");
    checkbox.type = "checkbox";
    checkbox.className = "folder-node__checkbox";
    checkbox.checked = state.selectedDocumentIds.includes(doc.id);
    checkbox.disabled = disabled;
    checkbox.addEventListener("click", (e) => { e.stopPropagation(); onToggleDocument(doc.id); });
    row.appendChild(checkbox);

    const icon = document.createElement("span");
    icon.className = "folder-node__icon";
    icon.innerHTML = FILE_ICON_SVG;
    row.appendChild(icon);

    const nameEl = document.createElement("span");
    nameEl.className = "folder-node__name";
    nameEl.textContent = doc.fileName;
    row.appendChild(nameEl);

    if (!disabled) row.addEventListener("click", () => onToggleDocument(doc.id));

    return row;
}

function buildFolderRow({ name, isRoot, active, checked, hasChildren, expanded, onNavigate, onToggleExpand, onToggleCheck, onRename, onDelete }) {
    const row = document.createElement("div");
    row.className = "folder-node__row" + (active ? " folder-node__row--active" : "");

    const toggle = document.createElement("span");
    toggle.className = "folder-node__toggle" +
        (hasChildren ? (expanded ? " folder-node__toggle--expanded" : "") : " folder-node__toggle--empty");
    toggle.innerHTML = CHEVRON_SVG;
    if (hasChildren) toggle.addEventListener("click", (e) => { e.stopPropagation(); onToggleExpand(); });
    row.appendChild(toggle);

    const checkbox = document.createElement("input");
    checkbox.type = "checkbox";
    checkbox.className = "folder-node__checkbox";
    checkbox.checked = checked;
    checkbox.addEventListener("click", (e) => { e.stopPropagation(); onToggleCheck(); });
    row.appendChild(checkbox);

    const icon = document.createElement("span");
    icon.className = "folder-node__icon";
    icon.innerHTML = FOLDER_ICON_SVG;
    row.appendChild(icon);

    const nameEl = document.createElement("span");
    nameEl.className = "folder-node__name";
    nameEl.textContent = name;
    row.appendChild(nameEl);

    row.addEventListener("click", onNavigate);

    if (!isRoot) {
        nameEl.addEventListener("dblclick", (e) => {
            e.stopPropagation();
            nameEl.contentEditable = "true";
            nameEl.focus();
            const range = document.createRange();
            range.selectNodeContents(nameEl);
            window.getSelection().removeAllRanges();
            window.getSelection().addRange(range);
        });
        nameEl.addEventListener("keydown", (e) => {
            if (e.key === "Enter") { e.preventDefault(); nameEl.blur(); }
            if (e.key === "Escape") { nameEl.textContent = name; nameEl.blur(); }
        });
        nameEl.addEventListener("blur", () => {
            nameEl.contentEditable = "false";
            const newName = nameEl.textContent.trim();
            if (newName && newName !== name) onRename(newName);
            else nameEl.textContent = name;
        });

        const del = document.createElement("button");
        del.className = "folder-node__delete";
        del.title = "Удалить папку";
        del.innerHTML = TRASH_SVG;
        del.addEventListener("click", (e) => { e.stopPropagation(); onDelete(); });
        row.appendChild(del);
    }

    return row;
}

function buildFolderNode(folder, handlers) {
    const { documents, selectedDocumentIds, currentFolderId, expandedFolderIds, folders } = state;

    const descendantIds = getDescendantFolderIds(folder.id, folders);
    const docIdsInFolder = documents.filter(d => descendantIds.includes(d.folderId)).map(d => d.id);
    const checked = docIdsInFolder.length > 0 && docIdsInFolder.every(id => selectedDocumentIds.includes(id));
    const hasSubfolders = folders.some(f => f.parentFolderId === folder.id);
    const hasOwnDocs = documents.some(d => (d.folderId || null) === folder.id);
    const hasChildren = hasSubfolders || hasOwnDocs;
    const expanded = expandedFolderIds.has(folder.id);

    const wrap = document.createElement("div");
    wrap.className = "folder-node";

    const row = buildFolderRow({
        name: folder.name,
        isRoot: false,
        active: currentFolderId === folder.id,
        checked,
        hasChildren,
        expanded,
        onNavigate: () => handlers.onNavigate(folder.id),
        onToggleExpand: () => handlers.onToggleExpand(folder.id),
        onToggleCheck: () => handlers.onToggleFolderSelect(docIdsInFolder, checked),
        onRename: (newName) => handlers.onRename(folder.id, newName),
        onDelete: () => handlers.onDelete(folder.id, folder.name),
    });
    wrap.appendChild(row);

    const childDocs = documents.filter(d => (d.folderId || null) === folder.id);

    if (expanded && (hasChildren || childDocs.length > 0)) {
        const childrenContainer = document.createElement("div");
        childrenContainer.className = "folder-node__children";
        folders.filter(f => f.parentFolderId === folder.id)
            .forEach(child => childrenContainer.appendChild(buildFolderNode(child, handlers)));
        childDocs.forEach(doc => childrenContainer.appendChild(buildFileRow(doc, handlers.onToggleDocument)));
        wrap.appendChild(childrenContainer);
    }

    return wrap;
}

export function renderFolderTree(els, handlers) {
    const { folders, documents, selectedDocumentIds, currentFolderId } = state;
    els.folderTree.innerHTML = "";

    const allDocIds = documents.map(d => d.id);
    const rootChecked = allDocIds.length > 0 && allDocIds.every(id => selectedDocumentIds.includes(id));

    const rootRow = buildFolderRow({
        name: "Корень",
        isRoot: true,
        active: currentFolderId === null,
        checked: rootChecked,
        hasChildren: folders.some(f => !f.parentFolderId),
        expanded: true,
        onNavigate: () => handlers.onNavigate(null),
        onToggleCheck: () => handlers.onToggleFolderSelect(allDocIds, rootChecked),
    });
    els.folderTree.appendChild(rootRow);

    const childrenContainer = document.createElement("div");
    childrenContainer.className = "folder-node__children";
    folders.filter(f => !f.parentFolderId)
        .forEach(folder => childrenContainer.appendChild(buildFolderNode(folder, handlers)));
    documents.filter(d => !d.folderId)
        .forEach(doc => childrenContainer.appendChild(buildFileRow(doc, handlers.onToggleDocument)));
    els.folderTree.appendChild(childrenContainer);
}

export function updateDocsBtnLabel(els) {
    const count = state.selectedDocumentIds.length;
    const chat = getActiveChat();

    if (count === 0) {
        // В Wiki-режиме без выбора файлов поиск идёт по всей библиотеке — поясняем это в лейбле
        els.docsBtnLabel.textContent = chat?.wikiMode ? "Все документы" : "Документы";
    } else {
        els.docsBtnLabel.textContent = `${count} ${count === 1 ? "документ" : count < 5 ? "документа" : "документов"}`;
    }
}

// ─── Рендеринг чатов ─────────────────────────────────────────────────────────

export function renderChatsList(els, onActivate) {
    els.chatsList.innerHTML = "";

    const active = getActiveChat();
    if (els.chatTitle) {
        els.chatTitle.textContent = active ? active.name : "RagAnalyzer";
    }

    if (state.chats.length === 0) {
        els.chatsList.innerHTML = `<div class="chats-empty">Нет чатов</div>`;
        return;
    }

    state.chats.forEach(chat => {
        const item = document.createElement("div");
        item.className = "chat-item" + (chat.id === state.activeChatId ? " chat-item--active" : "");

        item.innerHTML = `
            <span class="chat-item__name" title="Дважды кликните для переименования">${escapeHtml(chat.name)}</span>
            <button class="chat-item__delete" title="Удалить чат">
                <svg width="12" height="12" viewBox="0 0 14 14" fill="none">
                    <path d="M2 3.5h10M5.5 3.5V2.5h3v1M5 3.5l.5 8M9 3.5l-.5 8"
                          stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
                </svg>
            </button>`;

        item.addEventListener("click", (e) => {
            if (e.target.closest(".chat-item__delete")) return;
            if (e.target.closest(".chat-item__name")?.isContentEditable) return;
            onActivate(chat.id);
        });

        const nameEl = item.querySelector(".chat-item__name");
        nameEl.addEventListener("dblclick", (e) => {
            e.stopPropagation();
            nameEl.contentEditable = "true";
            nameEl.focus();
            const range = document.createRange();
            range.selectNodeContents(nameEl);
            window.getSelection().removeAllRanges();
            window.getSelection().addRange(range);
        });
        nameEl.addEventListener("keydown", (e) => {
            if (e.key === "Enter") { e.preventDefault(); nameEl.blur(); }
            if (e.key === "Escape") { nameEl.textContent = chat.name; nameEl.blur(); }
        });
        nameEl.addEventListener("blur", () => {
            nameEl.contentEditable = "false";
            renameChat(chat.id, nameEl.textContent);
            renderChatsList(els, onActivate);
        });

        item.querySelector(".chat-item__delete").addEventListener("click", (e) => {
            e.stopPropagation();
            deleteChat(chat.id, (deletedId) => {
                if (state.activeChatId === deletedId) {
                    if (state.chats.length > 0) {
                        onActivate(state.chats[0].id);
                    } else {
                        state.activeChatId = null;
                        state.messages = [];
                        rerenderChatMessages(els);
                    }
                }
                renderChatsList(els, onActivate);
            });
        });

        els.chatsList.appendChild(item);
    });
}

// ─── Рендеринг сообщений ─────────────────────────────────────────────────────

export function rerenderChatMessages(els) {
    els.chatMessages.innerHTML = "";
    const chat = getActiveChat();
    if (!chat || chat.messages.length === 0) {
        els.chatMessages.innerHTML = `
            <div class="empty-chat">
                <div class="empty-chat__title">Чат готов</div>
                <div class="empty-chat__text">Задайте первый вопрос</div>
            </div>`;
        return;
    }
    chat.messages.forEach(msg => addMessageElement(els, msg));
}

// Создаёт пустое сообщение ассистента с индикатором загрузки
// Возвращает объект с методами для дозаписи текста и финализации
export function createStreamingMessage(els) {
    const el = document.createElement("div");
    el.className = "message message--assistant";
    el.innerHTML = `
        <div class="message__avatar">R</div>
        <div class="message__body">
            <div class="message__role">RagAnalyzer</div>
            <div class="message__text message__text--markdown message__text--streaming"></div>
            <div class="message__typing">
                <span></span><span></span><span></span>
            </div>
        </div>`;

    els.chatMessages.appendChild(el);
    scrollToBottom(els.chatMessages);

    const textEl   = el.querySelector(".message__text--streaming");
    const typingEl = el.querySelector(".message__typing");
    let   rawText  = "";

    return {
        getRawText() { return rawText; },
        isConnected() { return el.isConnected; },

        appendToken(token) {
            const shouldFollowStream = isNearBottom(els.chatMessages);
            rawText += token;
            textEl.innerHTML = marked.parse(normalizeAnswerText(rawText));
            Prism.highlightAllUnder(textEl);
            scrollToBottomIfNear(els.chatMessages, shouldFollowStream);
        },
        finalize(sources = [], images = []) {
            const shouldFollowStream = isNearBottom(els.chatMessages);
            typingEl.remove();
            // Убираем thinking-блоки из накопленного текста
            rawText = rawText.replace(/<think>[\s\S]*?<\/think>/g, "").trim();
            if (rawText) {
                textEl.innerHTML = marked.parse(normalizeAnswerText(rawText));
                Prism.highlightAllUnder(textEl);
            }
            textEl.classList.add("streaming-done");

            if (images.length > 0) {
                const imgs = images.map(img => `
                    <div class="message__image-wrap">
                        <img class="message__image" src="${escapeHtml(img.url)}"
                             alt="Стр. ${escapeHtml(img.pageNumber)}" loading="lazy"/>
                        <div class="message__image-label">Стр. ${escapeHtml(img.pageNumber)}</div>
                    </div>`).join("");
                textEl.insertAdjacentHTML("afterend",
                    `<div class="message__images">${imgs}</div>`);
            }

            if (sources.length > 0) {
                const seen = new Set();
                const chips = sources
                    .filter(s => { const k = `${s.fileName}:${s.pageNumber}`; return seen.has(k) ? false : seen.add(k); })
                    .map(s => `<span class="source-chip">${escapeHtml(s.fileName)} · стр. ${escapeHtml(s.pageNumber)}</span>`)
                    .join("");
                el.querySelector(".message__body").insertAdjacentHTML("beforeend",
                    `<div class="message__sources"><div class="message__sources-title">Источники</div>${chips}</div>`);
            }

            scrollToBottomIfNear(els.chatMessages, shouldFollowStream);
            return rawText;
        }
    };
}

export function addMessageElement(els, { role, text, sources = [], images = [], streaming = false }) {
    const isUser = role === "user";

    let sourcesHtml = "";
    if (sources.length > 0) {
        const seen = new Set();
        const chips = sources
            .filter(s => { const k = `${s.fileName}:${s.pageNumber}`; return seen.has(k) ? false : seen.add(k); })
            .map(s => `<span class="source-chip">${escapeHtml(s.fileName)} · стр. ${escapeHtml(s.pageNumber)}</span>`)
            .join("");
        sourcesHtml = `<div class="message__sources"><div class="message__sources-title">Источники</div>${chips}</div>`;
    }

    let imagesHtml = "";
    if (!isUser && images.length > 0) {
        const imgs = images.map(img => `
            <div class="message__image-wrap">
                <img class="message__image" src="${escapeHtml(img.url)}"
                     alt="Стр. ${escapeHtml(img.pageNumber)}" loading="lazy" />
                <div class="message__image-label">Стр. ${escapeHtml(img.pageNumber)}</div>
            </div>`).join("");
        imagesHtml = `<div class="message__images">${imgs}</div>`;
    }

    const textHtml = isUser
        ? `<div class="message__text">${escapeHtml(text)}</div>`
        : `<div class="message__text message__text--markdown">${text ? marked.parse(normalizeAnswerText(text)) : ""}</div>`;
    const typingHtml = !isUser && streaming
        ? `<div class="message__typing"><span></span><span></span><span></span></div>`
        : "";

    const el = document.createElement("div");
    el.className = `message message--${role}`;
    el.innerHTML = `
        <div class="message__avatar">${isUser ? "Вы" : "R"}</div>
        <div class="message__body">
            <div class="message__role">${isUser ? "Вы" : "RagAnalyzer"}</div>
            ${textHtml}${typingHtml}${imagesHtml}${sourcesHtml}
        </div>`;

    if (!isUser) Prism.highlightAllUnder(el);

    els.chatMessages.appendChild(el);
    els.chatMessages.scrollTop = els.chatMessages.scrollHeight;
}
