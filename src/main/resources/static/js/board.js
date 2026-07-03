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
            // 每次拖曳開始重置插入位置，避免沿用上一次拖曳殘留的 dragIndex
            this.dragIndex = 0;
            ev.dataTransfer.effectAllowed = 'move';
        },
        onColDragOver(status) {
            this.dragOverCol = status;
            // 空欄或欄尾放置時預設插入尾端：卡片層級的 dragover 會再以精準位置覆蓋，
            // 若無此預設，拖到空白區時 dragIndex 會殘留舊值，導致 sendMove 送錯 targetIndex
            const count = this.tasksIn(status).length;
            // 卡片若原本就在本欄，移動後欄內數量不變，尾端索引需扣掉自己
            this.dragIndex = (this.dragging && this.dragging.status === status)
                ? Math.max(0, count - 1) : count;
        },
        onCardDragOver(status, idx) {
            // 懸停在卡片上時，以該卡片位置為插入點（.stop 阻擋冒泡，避免被欄層級的尾端預設覆蓋）
            this.dragOverCol = status;
            this.dragIndex = idx;
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
        // ---- 寫入操作：透過 STOMP 送出，伺服端驗證權限後廣播全體，本機畫面靠訂閱回饋更新 ----
        connectWs() {
            const sock = new SockJS('/ws');
            this.stomp = Stomp.over(sock);
            this.stomp.debug = null;   // 關閉冗長 log
            this.stomp.connect({}, () => {
                this.stomp.subscribe(`/topic/board/${boardId}/tasks`, f => this.onTaskMessage(JSON.parse(f.body)));
                this.stomp.subscribe(`/topic/board/${boardId}/presence`, f => this.onPresence(JSON.parse(f.body)));
                this.stomp.send(`/app/board/${boardId}/join`, {}, '');
            });
            window.addEventListener('beforeunload', () =>
                this.stomp.send(`/app/board/${boardId}/leave`, {}, ''));
        },
        onTaskMessage(msg) {
            // MOVE 帶全板快照直接覆蓋；其餘按類型增量更新，確保多人畫面一致
            if (msg.type === 'TASK_MOVE') { this.tasks = msg.payload; return; }
            if (msg.type === 'TASK_CREATE') { this.tasks.push(msg.payload); return; }
            if (msg.type === 'TASK_UPDATE') {
                const i = this.tasks.findIndex(t => t.id === msg.taskId);
                if (i >= 0) this.tasks[i] = msg.payload;
                return;
            }
            if (msg.type === 'TASK_DELETE') {
                this.tasks = this.tasks.filter(t => t.id !== msg.taskId);
            }
        },
        onPresence(msg) {
            if (msg.type === 'LEAVE') {
                this.onlineUsers = this.onlineUsers.filter(u => u.userId !== msg.userId);
            } else if (!this.onlineUsers.some(u => u.userId === msg.userId)) {
                this.onlineUsers.push(msg);
            }
        },
        sendCreate(form) {
            this.stomp.send(`/app/board/${boardId}/task/create`, {}, JSON.stringify(form));
        },
        sendUpdate(taskId, form) {
            this.stomp.send(`/app/board/${boardId}/task/update`,
                { taskId: taskId }, JSON.stringify(form));
        },
        sendMove(taskId, status, targetIndex) {
            this.stomp.send(`/app/board/${boardId}/task/${taskId}/move`, {},
                JSON.stringify({ status, targetIndex }));
        },
        sendDelete(taskId) {
            this.stomp.send(`/app/board/${boardId}/task/${taskId}/delete`, {}, '');
        },
    },
    mounted() { this.loadAll(); this.connectWs(); },
}).mount('#board-app');
