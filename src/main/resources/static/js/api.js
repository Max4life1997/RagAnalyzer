// ─── JWT helper ───────────────────────────────────────────────────────────────

function authHeaders(extra = {}) {
    const token = localStorage.getItem("rag_token");
    return token
        ? { "Authorization": "Bearer " + token, ...extra }
        : { ...extra };
}

function handleUnauthorized(res) {
    if (res.status === 401 || res.status === 403) {
        // Токен истёк — принудительный выход
        localStorage.removeItem("rag_token");
        localStorage.removeItem("rag_user");
        location.reload();
    }
    return res;
}

// ─── API ──────────────────────────────────────────────────────────────────────

export const api = {
    async getDocuments() {
        const res = handleUnauthorized(
            await fetch("/api/documents", { headers: authHeaders() })
        );
        if (!res.ok) throw new Error(`Ошибка загрузки документов: ${res.status}`);
        return res.json();
    },

    async ask(question, documentIds, history, adviceEnabled = false) {
        const res = handleUnauthorized(await fetch("/api/chat/ask", {
            method: "POST",
            headers: authHeaders({ "Content-Type": "application/json" }),
            body: JSON.stringify({ question, documentIds, history, adviceEnabled })
        }));
        if (!res.ok) throw new Error(`Ошибка запроса: ${res.status}`);
        return res.json();
    },

    async generateTitle(history) {
        const res = handleUnauthorized(await fetch("/api/chat/generate-title", {
            method: "POST",
            headers: authHeaders({ "Content-Type": "application/json" }),
            body: JSON.stringify({ history })
        }));
        if (!res.ok) throw new Error(`Ошибка генерации названия: ${res.status}`);
        return res.json();
    },

    async uploadDocument(file, onProgress, folderId = null) {
        return new Promise((resolve, reject) => {
            const formData = new FormData();
            formData.append("file", file);
            if (folderId) formData.append("folderId", folderId);

            const xhr = new XMLHttpRequest();
            xhr.open("POST", "/api/documents/upload");

            const token = localStorage.getItem("rag_token");
            if (token) xhr.setRequestHeader("Authorization", "Bearer " + token);

            xhr.upload.addEventListener("progress", (e) => {
                if (e.lengthComputable) onProgress(Math.round((e.loaded / e.total) * 100));
            });

            xhr.addEventListener("load", () => {
                if (xhr.status === 401 || xhr.status === 403) {
                    localStorage.removeItem("rag_token");
                    location.reload();
                    return;
                }
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
        const res = handleUnauthorized(
            await fetch(`/api/documents/${documentId}`, {
                method: "DELETE",
                headers: authHeaders()
            })
        );
        if (!res.ok) throw new Error(`Ошибка удаления: ${res.status}`);
    },

    askStream(question, documentIds, history, adviceEnabled = false, wikiMode = false) {
        return fetch("/api/chat/ask/stream", {
            method: "POST",
            headers: authHeaders({ "Content-Type": "application/json" }),
            body: JSON.stringify({ question, documentIds, history, adviceEnabled, wikiMode })
        });
    },

    // ─── Папки ──────────────────────────────────────────────────────────────

    async getFolders() {
        const res = handleUnauthorized(await fetch("/api/folders", { headers: authHeaders() }));
        if (!res.ok) throw new Error(`Ошибка загрузки папок: ${res.status}`);
        return res.json();
    },

    async createFolder(name, parentFolderId) {
        const res = handleUnauthorized(await fetch("/api/folders", {
            method: "POST",
            headers: authHeaders({ "Content-Type": "application/json" }),
            body: JSON.stringify({ name, parentFolderId })
        }));
        if (!res.ok) throw new Error(`Ошибка создания папки: ${res.status}`);
        return res.json();
    },

    async renameFolder(folderId, name) {
        const res = handleUnauthorized(await fetch(`/api/folders/${folderId}`, {
            method: "PUT",
            headers: authHeaders({ "Content-Type": "application/json" }),
            body: JSON.stringify({ name })
        }));
        if (!res.ok) throw new Error(`Ошибка переименования: ${res.status}`);
        return res.json();
    },

    async deleteFolder(folderId) {
        const res = handleUnauthorized(await fetch(`/api/folders/${folderId}`, {
            method: "DELETE",
            headers: authHeaders()
        }));
        if (!res.ok) throw new Error(`Ошибка удаления папки: ${res.status}`);
    },

    async moveDocument(documentId, folderId) {
        const res = handleUnauthorized(await fetch(`/api/documents/${documentId}/move`, {
            method: "PUT",
            headers: authHeaders({ "Content-Type": "application/json" }),
            body: JSON.stringify({ folderId })
        }));
        if (!res.ok) throw new Error(`Ошибка перемещения: ${res.status}`);
        return res.json();
    }
};
