package cz.crcs.ectester.common.test;

/**
 * @author Jan Jancar johny@neuromancer.sk
 */
public interface Testable {
    /**
     * @return Whether this Testable was OK.
     */
    boolean ok();

    /**
     * @return Whether an error happened.
     */
    boolean error();

    /**
     * @return Whether this runnable was run.
     */
    boolean hasRun();

    /**
     *
     */
    void reset();

    /**
     * Run this Runnable.
     *
     * @throws TestException If an unexpected exception/error is encountered.
     */
    void run() throws TestException;
}
