import { api } from "./api.js";

export const STATUS = {
    INDEXED: "INDEXED",
    PROCESSING: "PROCESSING",
    FAILED: "FAILED"
};

export const state = {
    documents: [],
    selectedDocumentIds: [],
    messages: [],
    chats: [],
    activeChatId: null
};

export function saveChats() {
    localStorage.setItem("rag_chats", JSON.stringify(state.chats));
}

export function loadChats() {
    try {
        state.chats = JSON.parse(localStorage.getItem("rag_chats") || "[]");
    } catch {
        state.chats = [];
    }
}

export function getActiveChat() {
    return state.chats.find(c => c.id === state.activeChatId) || null;
}

export function createChat(documentIds) {
    const chat = {
        id: crypto.randomUUID(),
        name: "Диалог",
        documentIds: [...documentIds],
        messages: []
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
