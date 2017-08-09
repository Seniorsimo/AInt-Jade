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

    /**
     * Lista di task pendenti (al momento inizializzata una sola volta nel Setup
     */
    private Queue<Task> tasks;

    private AID[] participants;

    /**
     * Mappa di 'stato' dei task in esecuzione. Più task possono essere
     * assegnati a participant differenti, quindi per ognuno è necessario
     * mantenerne un riferimento e tenere traccia del loro stato (running,
     * completed, error).
     */
    private Map<MessageTemplate, TaskBean> pendingTasks;

    @Override
    protected void setup() {
        this.tasks = new ArrayBlockingQueue<>(100, true);
        this.tasks.addAll(generateTasks(3));
        this.pendingTasks = new HashMap<>();
        System.out.println("Hello! Initiator " + getAID().getName() + " is ready.");

        // Aggiungo il behaviour per avviare un task.
        addBehaviour(new StartTask());
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
            list.add(new ImageTask());
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
    private class StartTask extends CyclicBehaviour {

        private Status status = Status.IDLE;
        private MessageTemplate mt;
        private int replysCount;
        private ACLMessage firstFree;
        private List<ACLMessage> refusingAID;
        private Date deadline;

        @Override
        public void action() {
            switch (status) {

                case IDLE:

                    // Se ho già avviato task, verifico se ho già ottenuto
                    // risposte, se si controllo lo stato del task e lo rimuovo
                    // se completo, o lo riaccodo se fallito
                    if (!pendingTasks.isEmpty()) {

                        // faccio una copia del set di chiavi, in modo da poter
                        // rimuovere le chiavi terminate o in errore dalla mappa
                        // all'interno di questo stesso loop
                        for (MessageTemplate m : new ArrayList<>(pendingTasks.keySet())) {

                            ACLMessage reply = myAgent.receive(m);
                            if (reply != null) {

                                if (reply.getPerformative() == ACLMessage.INFORM
                                        && reply.getContent().equals("done")) {

                                    System.out.println("Initiator - agent " + getAID().getName()
                                            + " terminated task successfully " + pendingTasks.get(m).getTask().getTaskType() + ".");

                                    pendingTasks.get(m).setStatus(TaskStatus.COMPLETED);

                                } else {

                                    System.out.println("Initiator - agent " + getAID().getName()
                                            + " terminated task with error " + pendingTasks.get(m).getTask().getTaskType() + ".");

                                    pendingTasks.get(m).setStatus(TaskStatus.ERROR);

                                    // Riaccodo il task
                                    tasks.add(pendingTasks.get(m).getTask());

                                }
                                pendingTasks.remove(m);
                            }

                        }

                    }

                    // Se ho ancora dei task da smistare, procedo a richiedere
                    // i participand liberi
                    if (!tasks.isEmpty()) {

                        Task task = tasks.peek(); // Guardo il primo task
                        updateParticipants();

                        if (participants.length > 0) {

                            ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                            cfp.setConversationId("task-execute");
                            for (AID participant : participants) {
                                cfp.addReceiver(participant);
                            }
                            cfp.setContent(task.getTaskType());
                            cfp.setReplyWith("cfp" + System.currentTimeMillis());

                            deadline = new Date(System.currentTimeMillis() + (1000 * 5));
                            cfp.setReplyByDate(deadline);
                            myAgent.send(cfp);

                            // Preparo il template per la risposta
                            mt = MessageTemplate.and(
                                    MessageTemplate.MatchConversationId(cfp.getConversationId()),
                                    MessageTemplate.MatchInReplyTo(cfp.getReplyWith())
                            );

                            System.out.println("Initiator - agent " + getAID().getName()
                                    + " looking for participants.");

                            replysCount = 0;
                            firstFree = null;
                            refusingAID = new ArrayList<>();
                            status = Status.WAITNG_RESPONSE;

                        } else {
                            block(3000);
                        }

                        // Se non ho più task da smistare e non ho task appesi
                        // rimuovo il behaviour, altrimenti attendo messaggi
                    } else if (pendingTasks.isEmpty()) {
                        myAgent.removeBehaviour(this);

                        System.out.println("Initiator - agent " + getAID().getName()
                                + " ended.");

                    } else {
                        block();
                    }

                    break;

                case WAITNG_RESPONSE:
                    Date now = new Date();
                    if (deadline.after(now)) {
                        ACLMessage reply = myAgent.receive(mt);
                        if (reply != null) {

                            if (reply.getPerformative() == ACLMessage.PROPOSE) {
                                if (firstFree == null) {
                                    firstFree = reply;
                                } else {
                                    refusingAID.add(reply);
                                }
                            }

                            replysCount++;

                            System.out.println("Initiator - agent " + getAID().getName()
                                    + " received response: " + replysCount
                                    + "/" + participants.length + ".");

                        } else {
                            block(deadline.getTime() - now.getTime()); // Attendo messaggi
                        }
                    }

                    if (replysCount >= participants.length || deadline.before(now)) {
                        status = firstFree == null ? Status.IDLE : Status.START;
                    }

                    break;

                case START:
                    try {
                        ACLMessage refusing = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
                        refusingAID.stream().forEach((reply) -> {
                            refusing.addReceiver(reply.getSender());
                            refusing.setConversationId(reply.getConversationId());
                        });
                        myAgent.send(refusing);

                        Task t = tasks.poll();
                        ACLMessage sendTask = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                        sendTask.addReceiver(firstFree.getSender());
                        sendTask.setConversationId("task-execute");
                        sendTask.setReplyWith("send" + System.currentTimeMillis());
                        sendTask.setContentObject(t);
                        myAgent.send(sendTask);

                        System.out.println("Initiator - agent " + getAID().getName()
                                + " sending task.");

                        // Preparo il template per la risposta
                        pendingTasks.put(MessageTemplate.and(
                                MessageTemplate.MatchConversationId(sendTask.getConversationId()),
                                MessageTemplate.MatchInReplyTo(sendTask.getReplyWith())
                        ), new TaskBean(t, TaskStatus.RUNNING));

                        status = Status.IDLE;

                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                    break;

            }

        }

        private void updateParticipants() {
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
            } catch (FIPAException e) {
                e.printStackTrace();
            }

            System.out.println("Initiator - agent " + getAID().getName()
                    + " updated participants, found: " + participants.length + ".");
        }

    }

    private enum Status {
        IDLE,
        WAITNG_RESPONSE,
        START
    }

}
