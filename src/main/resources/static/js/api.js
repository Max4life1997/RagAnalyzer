export const api = {
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

    async generateTitle(history) {
        const res = await fetch("/api/chat/generate-title", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ history })
        });
        if (!res.ok) throw new Error(`Ошибка генерации названия: ${res.status}`);
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
    },

    // Возвращает ReadableStream — вызывающий читает SSE события самостоятельно
    askStream(question, documentIds, history) {
        return fetch("/api/chat/ask/stream", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ question, documentIds, history })
        });
    }
};
