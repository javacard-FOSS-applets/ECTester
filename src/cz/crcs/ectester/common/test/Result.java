package cz.crcs.ectester.common.test;

/**
 * A Result of a Test. Has a Value and an optional String cause.
 *
 * @author Jan Jancar johny@neuromancer.sk
 */
public class Result {

    private Value value;
    private String cause;

    public Result(Value value) {
        this.value = value;
    }

    public Result(Value value, String cause) {
        this(value);
        this.cause = cause;
    }

    public Value getValue() {
        return value;
    }

    public String getCause() {
        return cause;
    }

    public boolean ok() {
        return value.ok();
    }

    public boolean compareTo(Result other) {
        if (other == null) {
            return false;
        }
        return value == other.value;
    }

    public boolean compareTo(Value other) {
        if (other == null) {
            return false;
        }
        return value == other;
    }

    /**
     * A result value of a Test.
     */
    public enum Value {
        SUCCESS(true),
        FAILURE(false),
        UXSUCCESS(false),
        XFAILURE(true),
        ERROR(false);

        private boolean ok;

        Value(boolean ok) {
            this.ok = ok;
        }

        public static Value fromExpected(ExpectedValue expected, boolean successful) {
            switch (expected) {
                case SUCCESS:
                    return successful ? SUCCESS : FAILURE;
                case FAILURE:
                    return successful ? UXSUCCESS : XFAILURE;
                case ANY:
                    return SUCCESS;
            }
            return SUCCESS;
        }

        public static Value fromExpected(ExpectedValue expected, boolean successful, boolean error) {
            if (error) {
                return ERROR;
            }
            return fromExpected(expected, successful);
        }

        public boolean ok() {
            return ok;
        }
    }

    /**
     * A possible expected value result of a Test.
     */
    public enum ExpectedValue {
        SUCCESS,
        FAILURE,
        ANY
    }
}
