package agency.carpediem.tv.prototype;

/** Orders cancellation and short publication/cleanup actions, never network or hashing work. */
final class UpdateOperation {
    static final class Token {
        private Token() {}
    }
    private Token current;

    /** Start a new operation. Capture this token before scheduling its asynchronous work. */
    synchronized Token begin() {
        current=new Token();
        return current;
    }

    synchronized boolean isCurrent(Token token) { return token!=null&&token==current; }

    /** All state/file/download mutations for a result belong inside this short action. */
    synchronized boolean commit(Token token,Runnable publication) {
        if(token==null||token!=current) return false;
        publication.run();
        return true;
    }

    /** Invalidate first, even if cancellation cleanup throws. */
    synchronized void cancel(Runnable cleanup) {
        current=null;
        cleanup.run();
    }
}
