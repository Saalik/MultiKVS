package com.bmartin.kvs;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import akka.cluster.ClusterEvent;
import akka.cluster.typed.Cluster;
import akka.cluster.typed.Subscribe;

import java.util.Set;

public class ClusterListener extends AbstractBehavior<ClusterListener.Command> {

    interface Command {
    }

    // internal adapted cluster events only
    private static class ListingResponse implements Command {
        final Receptionist.Listing listing;

        private ListingResponse(Receptionist.Listing listing) {
            this.listing = listing;
        }
    }

    private static final class ReachabilityChange implements Command {
        final ClusterEvent.ReachabilityEvent reachabilityEvent;

        ReachabilityChange(ClusterEvent.ReachabilityEvent reachabilityEvent) {
            this.reachabilityEvent = reachabilityEvent;
        }
    }

    private static final class MemberChange implements Command {
        final ClusterEvent.MemberEvent memberEvent;

        MemberChange(ClusterEvent.MemberEvent memberEvent) {
            this.memberEvent = memberEvent;
        }
    }

    public ClusterListener(ActorContext<Command> context, Cluster cluster) {
        super(context);
        context.getLog().debug("ClusterListener::ClusterListener()");

        ActorRef<Receptionist.Listing> messageAdapter = context.messageAdapter(Receptionist.Listing.class, ListingResponse::new);
        context.getSystem().receptionist()
                .tell(Receptionist.subscribe(
                        TransactionManagerActor.TRANSACTION_MANAGER_ACTOR_SERVICE_KEY, messageAdapter));

        ActorRef<ClusterEvent.ReachabilityEvent> reachabilityAdapter =
                context.messageAdapter(ClusterEvent.ReachabilityEvent.class, ReachabilityChange::new);
        cluster.subscriptions().tell(Subscribe.create(reachabilityAdapter, ClusterEvent.ReachabilityEvent.class));

        ActorRef<ClusterEvent.MemberEvent> memberEventAdapter =
                context.messageAdapter(ClusterEvent.MemberEvent.class, MemberChange::new);
        cluster.subscriptions().tell(Subscribe.create(memberEventAdapter, ClusterEvent.MemberEvent.class));
    }

    public static Behavior<Command> create(Cluster cluster) {
        return Behaviors.setup(context -> {
            return new ClusterListener(context, cluster);
        });
    }


    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(ListingResponse.class, response -> onListing(response.listing))
                .onMessage(ReachabilityChange.class, this::onReachabilityChange)
                .onMessage(MemberChange.class, this::onMemberChange)
                .build();
    }


    private Behavior<Command> onListing(Receptionist.Listing msg) {
        Set<ActorRef<TransactionManagerActor.Command>> listing = msg.getServiceInstances(TransactionManagerActor.TRANSACTION_MANAGER_ACTOR_SERVICE_KEY);
        System.out.println(">>>> " + listing);
//        if(listing.size() > 0) {
//            System.out.println(">>>> " + listing.iterator().next());
//        }

        return Behaviors.same();
    }

    private Behavior<Command> onReachabilityChange(ReachabilityChange event) {
        if (event.reachabilityEvent instanceof ClusterEvent.UnreachableMember) {
            getContext().getLog().info("Member detected as unreachable: {}", event.reachabilityEvent.member());
        } else if (event.reachabilityEvent instanceof ClusterEvent.ReachableMember) {
            getContext().getLog().info("Member back to reachable: {}", event.reachabilityEvent.member());
        }
        return this;
    }

    private Behavior<Command> onMemberChange(MemberChange event) {
        if (event.memberEvent instanceof ClusterEvent.MemberUp) {
            getContext().getLog().info("Member is up: {}", event.memberEvent.member());
        } else if (event.memberEvent instanceof ClusterEvent.MemberRemoved) {
            getContext().getLog().info("Member is removed: {} after {}",
                    event.memberEvent.member(),
                    ((ClusterEvent.MemberRemoved) event.memberEvent).previousStatus()
            );
        }
        return this;
    }

}
