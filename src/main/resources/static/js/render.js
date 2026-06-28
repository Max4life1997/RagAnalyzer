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

export function renderDocuments(els, onToggle, onDelete) {
    const filter = els.searchInput.value.trim().toLowerCase();
    const filtered = state.documents.filter(d => d.fileName.toLowerCase().includes(filter));

    els.documentsTableBody.innerHTML = "";

    filtered.forEach(doc => {
        const checked  = state.selectedDocumentIds.includes(doc.id);
        const disabled = doc.status !== STATUS.INDEXED;

        const row = document.createElement("tr");
        row.innerHTML = `
            <td><input type="checkbox" ${checked ? "checked" : ""} ${disabled ? "disabled" : ""} /></td>
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
                        <path d="M2 3.5h10M5.5 3.5V2.5h3v1M5 3.5l.5 8M9 3.5l-.5 8"
                              stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
                    </svg>
                </button>
            </td>`;

        row.querySelector('input[type="checkbox"]')?.addEventListener("change", () => onToggle(doc.id));
        row.querySelector(".btn-delete")?.addEventListener("click", () => onDelete(doc.id, doc.fileName));
        els.documentsTableBody.appendChild(row);
    });

    els.selectedCount.textContent = `Выбрано: ${state.selectedDocumentIds.length}`;
}

export function updateDocsBtnLabel(els) {
    const count = state.selectedDocumentIds.length;
    els.docsBtnLabel.textContent = count === 0
        ? "Документы"
        : `${count} ${count === 1 ? "документ" : count < 5 ? "документа" : "документов"}`;
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

        appendToken(token) {
            const shouldFollowStream = isNearBottom(els.chatMessages);
            rawText += token;
            textEl.innerHTML = marked.parse(rawText);
            Prism.highlightAllUnder(textEl);
            scrollToBottomIfNear(els.chatMessages, shouldFollowStream);
        },
        finalize(sources = [], images = []) {
            const shouldFollowStream = isNearBottom(els.chatMessages);
            typingEl.remove();
            // Убираем thinking-блоки из накопленного текста
            rawText = rawText.replace(/<think>[\s\S]*?<\/think>/g, "").trim();
            if (rawText) {
                textEl.innerHTML = marked.parse(rawText);
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

export function addMessageElement(els, { role, text, sources = [], images = [] }) {
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
        : `<div class="message__text message__text--markdown">${marked.parse(text)}</div>`;

    const el = document.createElement("div");
    el.className = `message message--${role}`;
    el.innerHTML = `
        <div class="message__avatar">${isUser ? "Вы" : "R"}</div>
        <div class="message__body">
            <div class="message__role">${isUser ? "Вы" : "RagAnalyzer"}</div>
            ${textHtml}${imagesHtml}${sourcesHtml}
        </div>`;

    if (!isUser) Prism.highlightAllUnder(el);

    els.chatMessages.appendChild(el);
    els.chatMessages.scrollTop = els.chatMessages.scrollHeight;
}
