package de.kopffrei.core.database.entity

import de.kopffrei.core.common.tasks.TaskView

/** Reine Logik (Ranking, Filter) arbeitet auf [TaskView], damit sie ohne Android testbar bleibt. */
fun TaskEntity.toView(): TaskView = TaskView(
    id = id,
    title = title,
    notes = notes,
    priority = priority,
    dueAt = dueAt,
    dueHasTime = dueHasTime,
    estimatedMinutes = estimatedMinutes,
    status = status,
    completedAt = completedAt,
)
