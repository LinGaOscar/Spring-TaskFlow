// Kanban 看板前端：初始資料走 REST，寫入操作走 STOMP（Task 8 接上）確保多人即時同步
const el = document.getElementById('board-app');
const boardId = el.dataset.boardId;
const csrfToken = document.querySelector('meta[name="_csrf"]').content;
const csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;

const { createApp } = Vue;

window.boardPage = createApp({
    data() {
        return {
            boardName: el.dataset.boardName,
            canWrite: el.dataset.canWrite === 'true' && el.dataset.archived !== 'true',
            archived: el.dataset.archived === 'true',
            columns: [
                { status: 'TODO',        label: '待辦' },
                { status: 'IN_PROGRESS', label: '進行中' },
                { status: 'DONE',        label: '完成' },
            ],
            tasks: [],
            members: [],
            templates: [],
            onlineUsers: [],
            dragging: null,     // 拖曳中的任務
            dragOverCol: null,  // 目前懸停的欄
            dragIndex: 0,       // 目前懸停的欄內位置
            modal: { open: false, taskId: null, templateId: null,
                     form: { title: '', description: '', assigneeId: null, dueDate: null, priority: 'MEDIUM' } },
        };
    },
    methods: {
        tasksIn(status) {
            return this.tasks.filter(t => t.status === status)
                .sort((a, b) => a.sortOrder - b.sortOrder);
        },
        isOverdue(t) {
            // 已完成的任務不再警示逾期，避免歷史卡片一片紅
            return t.dueDate && t.status !== 'DONE'
                && t.dueDate < new Date().toISOString().slice(0, 10);
        },
        exportUrl(fmt) { return `/api/boards/${boardId}/export?format=${fmt}`; },
        async loadAll() {
            // 全站 REST 端點統一回傳 ApiResponse {success,message,data} 信封，故取值前需先拆包並檢查 success
            const [tasksRes, membersRes, templatesRes] = await Promise.all([
                fetch(`/api/boards/${boardId}/tasks`).then(r => r.json()),
                fetch(`/api/boards/${boardId}/members`).then(r => r.json()),
                fetch(`/api/templates`).then(r => r.json()),
            ]);
            this.tasks = tasksRes.success ? tasksRes.data : [];
            this.members = membersRes.success ? membersRes.data : [];
            this.templates = templatesRes.success ? templatesRes.data : [];
        },
        openCreate() {
            this.modal = { open: true, taskId: null, templateId: null,
                form: { title: '', description: '', assigneeId: null, dueDate: null, priority: 'MEDIUM' } };
        },
        openEdit(t) {
            this.modal = { open: true, taskId: t.id, templateId: null,
                form: { title: t.title, description: t.description,
                        assigneeId: t.assigneeId, dueDate: t.dueDate, priority: t.priority } };
        },
        applyTemplate() {
            const tpl = this.templates.find(t => t.id === this.modal.templateId);
            if (!tpl) return;
            // 模板只帶入預設值，使用者仍可修改後再儲存
            this.modal.form.title = tpl.title;
            this.modal.form.description = tpl.description;
            this.modal.form.priority = tpl.priority;
        },
        onDragStart(t, ev) {
            this.dragging = t;
            ev.dataTransfer.effectAllowed = 'move';
        },
        onDrop(status) {
            if (!this.dragging || !this.canWrite) return;
            this.sendMove(this.dragging.id, status, this.dragIndex);
            this.dragging = null; this.dragOverCol = null;
        },
        saveTask() {
            if (!this.modal.form.title || !this.modal.form.title.trim()) {
                alert('標題不可為空'); return;
            }
            if (this.modal.taskId) this.sendUpdate(this.modal.taskId, this.modal.form);
            else this.sendCreate(this.modal.form);
            this.modal.open = false;
        },
        deleteTask() {
            if (!confirm('確定刪除此任務？')) return;
            this.sendDelete(this.modal.taskId);
            this.modal.open = false;
        },
        async archiveBoard() {
            if (!confirm('歸檔後看板將變為唯讀，確定歸檔？')) return;
            await fetch(`/api/boards/${boardId}/archive`,
                { method: 'POST', headers: { [csrfHeader]: csrfToken } });
            location.reload();
        },
        // ---- 寫入操作：Task 8 接上 STOMP，目前為唯讀展示骨架，故留空實作 ----
        sendCreate(form) { /* Task 8 接上 STOMP */ },
        sendUpdate(taskId, form) { /* Task 8 接上 STOMP */ },
        sendMove(taskId, status, targetIndex) { /* Task 8 接上 STOMP */ },
        sendDelete(taskId) { /* Task 8 接上 STOMP */ },
    },
    mounted() { this.loadAll(); },
}).mount('#board-app');
