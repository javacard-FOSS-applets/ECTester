package cz.crcs.ectester.common.output;

import cz.crcs.ectester.common.test.Test;
import cz.crcs.ectester.common.test.TestSuite;

/**
 * @author Jan Jancar johny@neuromancer.sk
 */
public interface TestWriter {
    void begin(TestSuite suite);

    void outputTest(Test t);

    void end();
}
