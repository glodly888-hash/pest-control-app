package com.sws.pest.domain.analytics;

import com.sws.pest.domain.workorder.Task;

import java.time.LocalDate;
import java.util.List;

/**
 * 员工工作台汇总（getDashboard）。
 *
 * 仅返回当前员工授权数据；planned/inProgress/completed/overdue 为本人任务全周期计数，
 * todayTasks 为 date 当天计划的任务列表。
 */
public final class Dashboard {

    private final LocalDate date;
    private final int planned;
    private final int inProgress;
    private final int completed;
    private final int overdue;
    private final int openRisks;
    private final List<Task> todayTasks;

    public Dashboard(LocalDate date, int planned, int inProgress, int completed,
                     int overdue, int openRisks, List<Task> todayTasks) {
        this.date = date;
        this.planned = planned;
        this.inProgress = inProgress;
        this.completed = completed;
        this.overdue = overdue;
        this.openRisks = openRisks;
        this.todayTasks = todayTasks;
    }

    public LocalDate date() { return date; }
    public int planned() { return planned; }
    public int inProgress() { return inProgress; }
    public int completed() { return completed; }
    public int overdue() { return overdue; }
    public int openRisks() { return openRisks; }
    public List<Task> todayTasks() { return todayTasks; }
}
