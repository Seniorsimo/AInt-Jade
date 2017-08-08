/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package aint.jade.task;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author simone
 */
public class ImageTask implements Task{

    private int step = 0;
    private static final int STEPS = 10;

    @Override
    public String getTaskType() {
        return "image";
    }

    @Override
    public void executeStep() {
        try {
            Thread.sleep(1000 * 2);
            step++;
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public boolean isEnded() {
        return step >= STEPS;
    }

    @Override
    public boolean isFailed() {
        // Questo task non fallisce mai per ora
        return false;
    }

    @Override
    public double getProgress() {
        return (step * 1.0) / STEPS;
    }
    
}
