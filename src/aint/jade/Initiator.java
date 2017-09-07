package aint.jade;

import aint.jade.task.ImageTask;
import aint.jade.task.Task;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Si realizzi un sistema ad agenti attraverso la piattaforma Jade nel quale un
 * agente (Initiator) distribuisce presso uno o più altri agenti (Participant)
 * un task da eseguire (ad esempio l'elaborazione di una immagine, oppure
 * l'applicazione di un filtro ad un file, o qualsiasi altra cosa vi suggerisca
 * la vostra fantasia) utilizzando il protocollo CNET. Gli agenti di tipo
 * initiator e participant (almeno due) si registrano presso il directory
 * facilitator del Main-Container di Jade con i rispettivi servizi [3, Sezione
 * 6].
 *
 * @author simone
 */
public class Initiator extends Agent {

    static final long SCAN_INTERVAL = 1000L * 10; //10 sec

    /**
     * Lista di task pendenti (al momento inizializzata una sola volta nel Setup
     */
    private Queue<Task> tasks;

    private AID[] participants;

    private int pendingTask = 0;

    @Override
    protected void setup() {
        this.tasks = new ArrayBlockingQueue<>(100, true);
        this.tasks.addAll(generateTasks(5));
        this.participants = new AID[0];
        System.out.println("Hello! Initiator " + getAID().getName() + " is ready.");

        // Aggiungo il behaviour per avviare un task.
        addBehaviour(new DispatchTask());
        addBehaviour(new DiscoverRunnerBev());

    }

    @Override
    protected void takeDown() {
        // Printout a dismissal message
        System.out.println("Initiator - agent " + getAID().getName() + " terminating.");
    }

    /*
    Metodo di comodo per popolare la coda di task
     */
    private List<Task> generateTasks(int n) {
        ArrayList<Task> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(new ImageTask("Image_" + i));
        }
        return list;
    }

    /**
     * Behaviour che si occupa dell'avvio di un task. Struttura. - IDLE: se ci
     * sono messaggi in coda, analizzo il primo, richiedo al directory
     * facilitator la lista dei participants, ed invio loro un messaggio per
     * richiederne la disponibilità all'esecuzione del task. - WAITING_RESPONSE:
     * attendo che tutti i destinatari del messaggio rispondano indicando la
     * loro disponibilità. - START_TASK: invio il task al primo participant
     * libero.
     *
     */
    private class DispatchTask extends CyclicBehaviour {

        boolean pendigRequest = false;
        private Date deadline;
        private MessageTemplate mt;
        private ACLMessage firstFree;
        private List<ACLMessage> refusingAID;
        private int replysCount;

        @Override
        public void action() {

            // Se ho finito i task, rimuovo il beahviour
            if (tasks.isEmpty()) {
                if (pendingTask <= 0) {
                    myAgent.removeBehaviour(this);
                } else {
                    block();
                }

            } // Se non ho richieste pendenti, faccio richiesta per il successivo task
            else if (!pendigRequest) {

                // Se conosco almeno un partecipant effettuo la richiesta
                if (participants.length > 0) {

                    deadline = sendCFP();

                    System.out.println("Initiator - agent " + getAID().getName()
                            + " looking for participants [" + tasks.peek().getName() + "].");

                    replysCount = 0;
                    firstFree = null;
                    refusingAID = new ArrayList<>();
                    pendigRequest = true;

                } // In caso contrario attendo
                else {

                    block();

                }
            } // Se ho richieste pendenti, gestisco le risposte
            else {

                Date now = new Date();
                if (deadline.after(now)) {

                    getCFPResponse();

                }

                if (replysCount >= participants.length || deadline.before(now)) {

                    if (deadline.before(now)) {
                        System.out.println("Initiator - agent " + getAID().getName()
                                + " Request timeout.");
                    }
                    if (firstFree != null) {
                        myAgent.addBehaviour(new TaskBehaviour(tasks.poll(), firstFree.getSender()));
                        pendingTask++;
                    }
                    refuse(refusingAID);
                    pendigRequest = false;

                }

            }
        }

        private void getCFPResponse() {
            ACLMessage reply = myAgent.receive(mt);
            if (reply != null) {

                if (reply.getPerformative() == ACLMessage.PROPOSE) {
                    if (firstFree == null) {
                        firstFree = reply;
                    } else {
                        refusingAID.add(reply);
                    }
                } else {
                    // do nothing if refused
                }

                replysCount++;

                System.out.println("Initiator - agent " + getAID().getName()
                        + " received response: " + replysCount
                        + "/" + participants.length + ".");

            } else {
                block(); // Attendo messaggi
            }
        }

        private Date sendCFP() {
            ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
            cfp.setConversationId("task-execute");
            for (AID participant : participants) {
                cfp.addReceiver(participant);
            }
            cfp.setContent(tasks.peek().getTaskType());
            cfp.setReplyWith("cfp" + System.currentTimeMillis());

            Date d = new Date(System.currentTimeMillis() + (1000 * 2));
            cfp.setReplyByDate(d);
            myAgent.send(cfp);

            // Preparo il template per la risposta
            mt = MessageTemplate.and(
                    MessageTemplate.MatchConversationId(cfp.getConversationId()),
                    MessageTemplate.MatchInReplyTo(cfp.getReplyWith())
            );
            return d;
        }

        private void refuse(List<ACLMessage> messages) {
            ACLMessage refusing = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
            messages.stream()
                    .filter(x -> x != null)
                    .forEach((reply) -> {
                        refusing.addReceiver(reply.getSender());
                        refusing.setConversationId(reply.getConversationId());
                    });
            myAgent.send(refusing);
        }

    }

    private class TaskBehaviour extends CyclicBehaviour {

        final Task task;
        final AID partner;
        boolean sended = false;
        private MessageTemplate mt;

        public TaskBehaviour(Task task, AID partner) {
            this.task = task;
            this.partner = partner;
        }

        @Override
        public void action() {
            if (!sended) {

                sendTask();

            } else {

                ACLMessage reply = myAgent.receive(mt);
                if (reply != null) {

                    if (reply.getPerformative() == ACLMessage.INFORM
                            && reply.getContent().equals("done")) {

                        System.out.println("Initiator - agent " + getAID().getName()
                                + " terminated task successfully [" + task.getName()+ "].");

                    } else {

                        System.out.println("Initiator - agent " + getAID().getName()
                                + " terminated task with error [" + task.getName() + "].");

                        // Riaccodo il task
                        tasks.add(task);

                    }

                    pendingTask--;

                } else {

                    block();

                }
            }

        }

        private void sendTask() {
            try {

                ACLMessage sendTask = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                sendTask.addReceiver(partner);
                sendTask.setConversationId("task-execute");
                sendTask.setReplyWith("send" + System.currentTimeMillis());
                sendTask.setContentObject(task);
                myAgent.send(sendTask);

                System.out.println("Initiator - agent " + getAID().getName()
                        + " sending task [" + task.getName() + "].");

                // Preparo il template per la risposta
                mt = MessageTemplate.and(
                        MessageTemplate.MatchConversationId(sendTask.getConversationId()),
                        MessageTemplate.MatchInReplyTo(sendTask.getReplyWith())
                );

                sended = true;

            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

    }

    private class DiscoverRunnerBev extends CyclicBehaviour {

        private Date lastScan = null;

        @Override
        public void action() {
            Date now = new Date();
            if (lastScan == null || new Date(lastScan.getTime() + SCAN_INTERVAL).before(now)) {
                updatePartecipants();
                lastScan = new Date();
            } else {
                long missing = (lastScan.getTime() + SCAN_INTERVAL) - now.getTime();
                block(missing > 0 ? missing : 0);
            }
        }

        private void updatePartecipants() {

            System.out.println("Updating runners");
            DFAgentDescription dfd = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("task-executor");
            dfd.addServices(sd);

            try {
                DFAgentDescription[] results = DFService.search(myAgent, dfd);
                participants = new AID[results.length];
                for (int i = 0; i < results.length; i++) {
                    participants[i] = results[i].getName();
                }
                System.out.println("Found " + participants.length + " runners.");
            } catch (FIPAException e) {
                System.out.println("Error finding runner: " + e);
            }
        }

    }

}
