/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package aint.jade;

import aint.jade.task.Task;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author simone
 */
public class Participant extends Agent {

    private Set<String> runnableTasks;
    private boolean working;
    private ACLMessage executionReply;
    private Task executionTask;

    @Override
    protected void setup() {

        runnableTasks = new HashSet<>();
        runnableTasks.add("image");
        working = false;

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("task-executor");
        sd.setName("JADE-task-execution");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException e) {
            e.printStackTrace();
        }

        addBehaviour(new ListenForTask());

        System.out.println("Hello! Participant " + getAID().getName() + " is ready.");
    }

    @Override
    protected void takeDown() {

        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            e.printStackTrace();
        }

        System.out.println("Participant - agent " + getAID().getName() + " terminating.");
    }

    private class ListenForTask extends CyclicBehaviour {

        @Override
        public void action() {

            // Verifica le richieste da parte dell'initiator e ripsondi in base
            // al roprio stato.
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
            ACLMessage message = myAgent.receive(mt);
            if (message != null) {
                
                ACLMessage reply = message.createReply();

                // Se non vi sono lavori in corso e il task richiesto è
                // compreso nei task che il participant può eseguire
                // allora proponiti per tale task, altrimenti rifiuta.
                if (!working && runnableTasks.contains(message.getContent())) {
                    reply.setPerformative(ACLMessage.PROPOSE);
                    reply.setContent("ready");
                    
                    System.out.println("Participant - agent " + getAID().getName()
                            + " received new request: PROPOSING.");
                    
                } else {
                    reply.setPerformative(ACLMessage.REFUSE);
                    reply.setContent("busy");
                    
                    System.out.println("Participant - agent " + getAID().getName()
                            + " received new request: REFUSING.");
                    
                }

                myAgent.send(reply);

            }

            if (working) {

                executionTask.executeStep();
                System.out.println("Participant - agent " + getAID().getName()
                        + ": " + (executionTask.getProgress() * 100) + "%.");

                if (executionTask.isFailed()) {

                    executionReply.setPerformative(ACLMessage.FAILURE);
                    executionReply.setContent("failed");
                    myAgent.send(executionReply);
                    working = false;
                    
                    System.out.println("Participant - agent " + getAID().getName()
                            + " execution FAILED.");

                } else if (executionTask.isEnded()) {

                    executionReply.setPerformative(ACLMessage.CONFIRM);
                    executionReply.setContent("ended");
                    myAgent.send(executionReply);
                    working = false;
                    
                    System.out.println("Participant - agent " + getAID().getName()
                            + " execution ENDED.");

                }

            } else {

                mt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
                message = myAgent.receive(mt);
                if (message != null) {

                    try {

                        executionTask = (Task) message.getContentObject();
                        executionReply = message.createReply();

                        working = true;
                        
                        System.out.println("Participant - agent " + getAID().getName()
                            + " received task: STARTING.");

                    } catch (UnreadableException ex) {
                        ex.printStackTrace();
                    }

                } else {
                    block();
                }

            }
        }

    }

}
