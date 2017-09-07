/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package aint.jade.task;

import java.io.Serializable;

/**
 *
 * @author simone
 */
public interface Task extends Serializable{
    
    public void executeStep();
    
    public boolean isEnded();
    
    public boolean isFailed();
    
    public double getProgress();
    
    public String getTaskType();
    
    public String getName();
    
}
