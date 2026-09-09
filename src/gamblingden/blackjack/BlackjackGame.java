package gamblingden.blackjack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Adapted from Interastral Peace Casino's BlackjackGame and Deck by Emanon6 and
 * WolframSegler. Non-commercial reuse terms: see THIRD_PARTY_NOTICES.txt.
 * Changes: token account, guarded bets, dealer peek, split settlement, split aces,
 * exact integer payouts, incremental dealer play, and safe close settlement. */
public final class BlackjackGame {
    public enum State { READY, PLAYER, DEALER, RESULT }
    public enum Suit { SPADES, HEARTS, DIAMONDS, CLUBS }
    public record Card(int rank, Suit suit) {
        public Card {
            if (rank < 1 || rank > 13 || suit == null) throw new IllegalArgumentException("Invalid card");
        }
        public int value() { return rank == 1 ? 11 : Math.min(rank, 10); }
        public String symbol() { return switch (rank) { case 1 -> "A"; case 11 -> "J"; case 12 -> "Q"; case 13 -> "K"; default -> "" + rank; }; }
    }
    public interface Account {
        int balance();
        boolean take(int amount);
        int pay(int amount);
    }
    public static final class Hand {
        private final List<Card> cards = new ArrayList<Card>();
        private int bet, returned;
        private boolean split, done;
        private String outcome = "";
        private Hand(int bet) { this.bet = bet; }
        public List<Card> cards() { return Collections.unmodifiableList(cards); }
        public int bet() { return bet; }
        public int returned() { return returned; }
        public String outcome() { return outcome; }
        public int value() {
            int total = 0, aces = 0;
            for (Card card : cards) { total += card.value(); if (card.rank == 1) aces++; }
            while (total > 21 && aces-- > 0) total -= 10;
            return total;
        }
        public boolean soft() {
            int low = 0; boolean ace = false;
            for (Card card : cards) { low += card.rank == 1 ? 1 : card.value(); ace |= card.rank == 1; }
            return ace && low + 10 <= 21;
        }
        public boolean bust() { return value() > 21; }
        public boolean natural() { return !split && cards.size() == 2 && value() == 21; }
    }
    private final Account account;
    private final Random random;
    private final List<Card> shoe = new ArrayList<Card>();
    private final List<Hand> hands = new ArrayList<Hand>();
    private Hand dealer = new Hand(0);
    private State state = State.READY;
    private int active, invested, paid;
    private long rounds, sessionNet;

    public BlackjackGame(Account account, Random random) { this.account = account; this.random = random; }
    public State state() { return state; }
    public boolean playing() { return state == State.PLAYER || state == State.DEALER; }
    public List<Hand> hands() { return Collections.unmodifiableList(hands); }
    public Hand dealer() { return dealer; }
    public int active() { return active; }
    public int invested() { return invested; }
    public int paid() { return paid; }
    public long rounds() { return rounds; }
    public long sessionNet() { return sessionNet; }
    public boolean holeRevealed() { return state != State.PLAYER; }
    private Hand current() { return state == State.PLAYER ? hands.get(active) : null; }

    public void clearResult() {
        if (playing()) return;
        state = State.READY; hands.clear(); dealer = new Hand(0); invested = paid = active = 0;
    }
    public boolean canDeal(int bet) {
        // Even bets make the 3:2 bonus exact in whole tokens. Reserve room for a natural.
        return !playing() && bet >= 2 && bet <= 1000 && bet % 2 == 0 && account.balance() >= bet
                && (long) account.balance() + bet * 3L / 2 <= Integer.MAX_VALUE;
    }
    public boolean deal(int bet) {
        if (!canDeal(bet) || !account.take(bet)) return false;
        clearResult();
        if (shoe.size() < 52) shuffle();
        invested = bet;
        Hand player = new Hand(bet); hands.add(player);
        player.cards.add(draw()); dealer.cards.add(draw());
        player.cards.add(draw()); dealer.cards.add(draw());
        state = State.PLAYER;
        // Peek before any extra player stakes: a dealer natural always beats a drawn 21.
        if (dealer.natural() || player.natural()) settle();
        return true;
    }
    private void shuffle() {
        shoe.clear();
        for (int deck = 0; deck < 6; deck++) for (Suit suit : Suit.values())
            for (int rank = 1; rank <= 13; rank++) shoe.add(new Card(rank, suit));
        Collections.shuffle(shoe, random);
    }
    private Card draw() {
        // At least 52 cards at the start; a round cannot exhaust a six-deck shoe before busting.
        if (shoe.isEmpty()) throw new IllegalStateException("Blackjack shoe exhausted");
        return shoe.remove(shoe.size() - 1);
    }
    public boolean hit() {
        Hand hand = current(); if (hand == null) return false;
        hand.cards.add(draw());
        if (hand.value() >= 21) endHand();
        return true;
    }
    public boolean stand() {
        if (current() == null) return false;
        endHand(); return true;
    }
    private boolean canAddBet(int extra) {
        return account.balance() >= extra && (long) account.balance() - extra + 2L * (invested + extra) <= Integer.MAX_VALUE;
    }
    public boolean canDouble() {
        Hand hand = current();
        return hand != null && hand.cards.size() == 2 && canAddBet(hand.bet);
    }
    public boolean doubleDown() {
        if (!canDouble()) return false;
        Hand hand = current();
        if (!account.take(hand.bet)) return false;
        invested += hand.bet; hand.bet *= 2;
        hand.cards.add(draw()); endHand(); return true;
    }
    public boolean canSplit() {
        Hand hand = current();
        return hand != null && hands.size() == 1 && hand.cards.size() == 2
                && hand.cards.get(0).rank == hand.cards.get(1).rank && canAddBet(hand.bet);
    }
    public boolean split() {
        if (!canSplit()) return false;
        Hand first = current();
        if (!account.take(first.bet)) return false;
        invested += first.bet;
        Hand second = new Hand(first.bet); second.cards.add(first.cards.remove(1));
        first.split = second.split = true; hands.add(second);
        first.cards.add(draw()); second.cards.add(draw());
        // One split only. Split aces receive one additional card each, then stand.
        if (first.cards.get(0).rank == 1) { first.done = second.done = true; startDealer(); }
        else { if (second.value() == 21) second.done = true; if (first.value() == 21) endHand(); }
        return true;
    }
    private void endHand() {
        hands.get(active).done = true;
        while (++active < hands.size()) if (!hands.get(active).done) return;
        startDealer();
    }
    private void startDealer() {
        state = State.DEALER;
        boolean survivor = false;
        for (Hand hand : hands) survivor |= !hand.bust();
        if (!survivor) settle();
    }
    /** One visible dealer draw at a time; the dealer stands on all 17s, including soft 17. */
    public void stepDealer() {
        if (state != State.DEALER) return;
        if (dealer.value() < 17) dealer.cards.add(draw());
        if (dealer.value() >= 17) settle();
    }
    public void finish() {
        while (state == State.PLAYER) stand();
        while (state == State.DEALER) stepDealer();
    }
    private void settle() {
        if (state == State.RESULT) return;
        int returned = 0;
        for (Hand hand : hands) {
            if (hand.bust()) hand.outcome = "Bust";
            else if (dealer.natural()) {
                if (hand.natural()) { hand.returned = hand.bet; hand.outcome = "Push"; }
                else hand.outcome = "Dealer blackjack";
            } else if (hand.natural()) { hand.returned = hand.bet * 5 / 2; hand.outcome = "Blackjack"; }
            else if (dealer.bust() || hand.value() > dealer.value()) { hand.returned = hand.bet * 2; hand.outcome = "Win"; }
            else if (hand.value() == dealer.value()) { hand.returned = hand.bet; hand.outcome = "Push"; }
            else hand.outcome = "Loss";
            returned += hand.returned;
        }
        state = State.RESULT; // Guard before touching the real account.
        paid = returned == 0 ? 0 : account.pay(returned);
        rounds++; sessionNet += paid - invested;
    }
    public String result() {
        if (state != State.RESULT) return "";
        String outcome = hands.size() == 1 ? hands.get(0).outcome : "Round complete";
        return outcome + "  |  Payout: " + paid + (paid==1?" token":" tokens");
    }
}
