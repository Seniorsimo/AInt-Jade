/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package aint.jade;

import aint.jade.task.Task;

/**
 * Contenitore per i task, usato dall'initiator per tenere traccia di un Task
 * e del suo stato di esecuzione.
 * @author simone
 */
public class TaskBean {

    private Task task;
    private TaskStatus status;

    public TaskBean(Task task, TaskStatus status) {
        this.task = task;
        this.status = status;
    }

    public Task getTask() {
        return task;
    }

    public void setTask(Task task) {
        this.task = task;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

}
