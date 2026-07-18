import { api } from "./api.js";

export const STATUS = {
    INDEXED: "INDEXED",
    PROCESSING: "PROCESSING",
    FAILED: "FAILED"
};

const DEFAULT_CHAT_NAME = "\u0414\u0438\u0430\u043b\u043e\u0433";

export const state = {
    documents: [],
    selectedDocumentIds: [],
    messages: [],
    chats: [],
    activeChatId: null,
    folders: [],
    currentFolderId: null,       // null = корень
    expandedFolderIds: new Set() // какие папки развёрнуты в дереве
};

let _chatsKey = "rag_chats";

export function saveChats() {
    localStorage.setItem(_chatsKey, JSON.stringify(state.chats));
}

export function loadChats(key = "rag_chats") {
    _chatsKey = key;
    try {
        state.chats = JSON.parse(localStorage.getItem(_chatsKey) || "[]");
    } catch {
        state.chats = [];
    }
}

function createId() {
    if (globalThis.crypto?.randomUUID) {
        return crypto.randomUUID();
    }
    return `chat-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

function createChatName() {
    const baseName = DEFAULT_CHAT_NAME;
    const usedNames = new Set(state.chats.map(chat => chat.name));
    if (!usedNames.has(baseName)) {
        return baseName;
    }

    let index = 2;
    while (usedNames.has(`${baseName} ${index}`)) {
        index++;
    }
    return `${baseName} ${index}`;
}

export function getActiveChat() {
    return state.chats.find(c => c.id === state.activeChatId) || null;
}

export function createChat(documentIds, wikiMode = false) {
    const chat = {
        id: createId(),
        name: createChatName(),
        documentIds: [...documentIds],
        messages: [],
        wikiMode
    };
    state.chats.unshift(chat);
    saveChats();
    return chat;
}

export function deleteChat(chatId, onAfter) {
    state.chats = state.chats.filter(c => c.id !== chatId);
    saveChats();
    onAfter(chatId);
}

export function renameChat(chatId, newName) {
    const chat = state.chats.find(c => c.id === chatId);
    if (chat && newName.trim()) {
        chat.name = newName.trim();
        saveChats();
    }
}

export async function tryGenerateTitle(onSuccess) {
    const chat = getActiveChat();
    if (!chat) return;

    const userMessages = chat.messages.filter(m => m.role === "user");
    if (userMessages.length !== 2 || chat.name !== "Диалог") return;

    try {
        const history = chat.messages.map(m => ({ role: m.role, text: m.text }));
        const data = await api.generateTitle(history);
        if (data.title) {
            chat.name = data.title;
            saveChats();
            onSuccess();
        }
    } catch (e) {
        console.error("Не удалось сгенерировать название", e);
    }
}
