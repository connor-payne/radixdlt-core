package org.radix.network2.addressbook;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * An extension of {@link Predicate} specifically for the {@link Peer} type.
 * Typically used for filtering in stream pipelines.
 */
@FunctionalInterface
public interface PeerPredicate extends Predicate<Peer> {

	/**
     * Evaluates this predicate on the given argument.
     *
     * @param p the input argument
     * @return {@code true} if the input argument matches the predicate,
     * otherwise {@code false}
     */
	@Override
    boolean test(Peer p);

    /**
     * Returns a composed predicate that represents a short-circuiting logical
     * AND of this predicate and another.  When evaluating the composed
     * predicate, if this predicate is {@code false}, then the {@code other}
     * predicate is not evaluated.
     * <p>
     * Any exceptions thrown during evaluation of either predicate are relayed
     * to the caller; if evaluation of this predicate throws an exception, the
     * {@code other} predicate will not be evaluated.
     *
     * @param other a predicate that will be logically-ANDed with this predicate
     * @return a composed predicate that represents the short-circuiting logical
     * AND of this predicate and the {@code other} predicate
     * @throws NullPointerException if other is null
     */
    @Override
	default PeerPredicate and(Predicate<? super Peer> other) {
        Objects.requireNonNull(other);
        return t -> test(t) && other.test(t);
    }

    /**
     * Returns a predicate that represents the logical negation of this
     * predicate.
     *
     * @return a predicate that represents the logical negation of this
     * predicate
     */
    @Override
	default PeerPredicate negate() {
        return p -> !test(p);
    }

    /**
     * Returns a composed predicate that represents a short-circuiting logical
     * OR of this predicate and another.  When evaluating the composed
     * predicate, if this predicate is {@code true}, then the {@code other}
     * predicate is not evaluated.
     * <p>
     * Any exceptions thrown during evaluation of either predicate are relayed
     * to the caller; if evaluation of this predicate throws an exception, the
     * {@code other} predicate will not be evaluated.
     *
     * @param other a predicate that will be logically-ORed with this predicate
     * @return a composed predicate that represents the short-circuiting logical
     * OR of this predicate and the {@code other} predicate
     * @throws NullPointerException if other is null
     */
    @Override
	default PeerPredicate or(Predicate<? super Peer> other) {
        Objects.requireNonNull(other);
        return p -> test(p) || other.test(p);
    }

}
