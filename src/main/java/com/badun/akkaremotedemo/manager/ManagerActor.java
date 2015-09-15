package com.badun.akkaremotedemo.manager;

import akka.actor.*;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.AskableActorSelection;
import akka.util.Timeout;
import com.badun.akkaremotedemo.message.ManagerTimeout;
import com.badun.akkaremotedemo.message.PieceOfWork;
import com.badun.akkaremotedemo.message.WorkDone;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Created by Artsiom Badun.
 */
public class ManagerActor extends AbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    private final String initialWorkerPath;
    private int taskNumber = 1000;

    public ManagerActor(String initialWorkerPath) {
        receive(ReceiveBuilder
                .match(ReceiveTimeout.class, this::handleTimeoutMessage)
                .match(WorkDone.class, this::handleWorkerMessage)
                .matchAny(this::unhandled)
                .build());
        this.initialWorkerPath = initialWorkerPath;
        context().setReceiveTimeout(Duration.create(5, TimeUnit.SECONDS));
        log.debug("Created manager actor with initialWorkerPath: " + initialWorkerPath);
    }

    private void handleTimeoutMessage(ReceiveTimeout timeout) {
        try {
            ActorRef worker = getWorker();
            worker.tell(new PieceOfWork("Task N" + taskNumber++), self());
            log.info("[MANAGER] Manager send a peace of work to worker after timeout.");
        } catch (Exception e) {
            log.error("Worker not found. " + e.getMessage());
        }
    }

    private ActorRef getWorker() throws Exception {
        ActorSelection actorSelection = getContext().actorSelection(initialWorkerPath);
        AskableActorSelection askableActorSelection = new AskableActorSelection(actorSelection);
        Timeout timeout = new Timeout(5, TimeUnit.SECONDS);
        Future<Object> fut = askableActorSelection.ask(new Identify(1), timeout);
        ActorIdentity ident = (ActorIdentity)Await.result(fut, timeout.duration());
        ActorRef ref = ident.getRef();
        if (ref == null) {
            throw new RuntimeException("Can't select a remote worker.");
        }
        return ref;
    }

    private void handleWorkerMessage(WorkDone message) {
        sender().tell(new PieceOfWork("Task N" + taskNumber++), self());
        log.info("[MANAGER] Manager send a peace of work to worker by worker request.");
    }
}