/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package aint.jade.task;

/**
 *
 * @author simone
 */
public class ImageTask implements Task{

    private final String name;
    private int step = 0;
    private static final int STEPS = 10;

    public ImageTask(String name){
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public String getTaskType() {
        return "image";
    }

    @Override
    public void executeStep() {
        try {
            Thread.sleep(100 * 3);
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
