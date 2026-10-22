(() => {
    const dialog = document.getElementById("assistant-dialog");
    const form = document.getElementById("assistant-form");
    const question = document.getElementById("question");
    const code = document.getElementById("workshop-code");
    const access = document.getElementById("access-details");
    const status = document.getElementById("assistant-status");
    const button = document.getElementById("ask-button");
    const result = document.getElementById("assistant-result");
    const answer = document.getElementById("assistant-answer");
    const request = document.getElementById("assistant-request");
    const storageKey = "trail-workshop-code";

    try {
        code.value = sessionStorage.getItem(storageKey) || "";
    } catch {
        status.textContent = "Browser storage is unavailable. Enter the workshop code for this visit.";
    }
    access.open = !code.value;
    document.getElementById("open-assistant").addEventListener("click", () => dialog.showModal());
    document.getElementById("close-assistant").addEventListener("click", () => dialog.close());
    document.querySelectorAll("[data-question]").forEach(suggestion => {
        suggestion.addEventListener("click", () => {
            question.value = suggestion.dataset.question;
            question.focus();
        });
    });

    fetch("/api/assistant/status")
        .then(response => {
            if (!response.ok) throw new Error("Availability check failed");
            return response.json();
        })
        .then(state => {
            button.disabled = !state.enabled;
            status.textContent = state.enabled
                ? "Workshop AI is configured. Enter your access code to ask a question."
                : "AI is offline here. The catalog works without Azure credentials.";
        })
        .catch(() => {
            status.textContent = "Could not check AI availability. Reload to retry; the catalog is still available.";
        });

    form.addEventListener("submit", async event => {
        event.preventDefault();
        if (!question.value.trim()) {
            status.textContent = "Enter an inventory question first.";
            question.focus();
            return;
        }
        if (!code.value.trim()) {
            access.open = true;
            code.focus();
            status.textContent = "Enter the workshop access code supplied by the presenter.";
            return;
        }
        button.disabled = true;
        button.textContent = "Checking inventory...";
        result.hidden = false;
        result.classList.remove("is-error");
        answer.textContent = "Looking up product facts...";
        request.textContent = "";
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), 35000);
        try {
            const response = await fetch("/api/assistant/messages", {
                method: "POST",
                headers: {"Content-Type": "application/json", "X-Workshop-Code": code.value.trim()},
                body: JSON.stringify({message: question.value.trim()}),
                signal: controller.signal
            });
            const payload = await response.json();
            if (!response.ok) {
                if (response.status === 401) access.open = true;
                throw new Error(payload.detail || "The assistant request failed. Please retry.");
            }
            if (typeof payload.reply !== "string" || !payload.reply.trim()) {
                throw new Error("The assistant returned an invalid response. Please retry.");
            }
            answer.textContent = payload.reply;
            request.textContent = payload.traceId ? `Trace ${payload.traceId}` : `Request ${payload.requestId}`;
            status.textContent = "Answer received. Each new question starts fresh.";
            try {
                sessionStorage.setItem(storageKey, code.value.trim());
                access.open = false;
            } catch {
                status.textContent = "Answered. Browser storage is unavailable; the code will not survive a reload.";
            }
        } catch (error) {
            result.classList.add("is-error");
            answer.textContent = error.name === "AbortError"
                ? "The request took too long. Please retry or use the catalog."
                : error.message;
            status.textContent = "No answer was generated successfully. The catalog is still available.";
        } finally {
            clearTimeout(timer);
            button.disabled = false;
            button.textContent = "Ask the inventory";
        }
    });
})();
