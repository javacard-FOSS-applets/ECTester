package cz.crcs.ectester.reader.output;

import cz.crcs.ectester.common.output.BaseYAMLTestWriter;
import cz.crcs.ectester.common.test.TestSuite;
import cz.crcs.ectester.common.test.Testable;
import cz.crcs.ectester.common.util.ByteUtil;
import cz.crcs.ectester.reader.CardMngr;
import cz.crcs.ectester.reader.command.Command;
import cz.crcs.ectester.reader.response.Response;
import cz.crcs.ectester.reader.test.CardTestSuite;
import cz.crcs.ectester.reader.test.CommandTestable;

import javax.smartcardio.CardException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author Jan Jancar johny@neuromancer.sk
 */
public class YAMLTestWriter extends BaseYAMLTestWriter {
    public YAMLTestWriter(PrintStream output) {
        super(output);
    }

    private Map<String, Object> commandObject(Command c) {
        Map<String, Object> commandObj = new HashMap<>();
        commandObj.put("apdu", ByteUtil.bytesToHex(c.getAPDU().getBytes()));
        return commandObj;
    }

    private Map<String, Object> responseObject(Response r) {
        Map<String, Object> responseObj = new HashMap<>();
        responseObj.put("successful", r.successful());
        responseObj.put("apdu", ByteUtil.bytesToHex(r.getAPDU().getBytes()));
        responseObj.put("natural_sw", Short.toUnsignedInt(r.getNaturalSW()));
        List<Integer> sws = new LinkedList<>();
        for (int i = 0; i < r.getNumSW(); ++i) {
            sws.add(Short.toUnsignedInt(r.getSW(i)));
        }
        responseObj.put("sws", sws);
        responseObj.put("duration", r.getDuration());
        responseObj.put("desc", r.getDescription());
        return responseObj;
    }

    @Override
    protected Map<String, Object> testableObject(Testable t) {
        if (t instanceof CommandTestable) {
            CommandTestable cmd = (CommandTestable) t;
            Map<String, Object> result = new HashMap<>();
            result.put("type", "command");
            result.put("command", commandObject(cmd.getCommand()));
            result.put("response", responseObject(cmd.getResponse()));
            return result;
        }
        return null;
    }

    private Map<String, Object> cplcObject(CardMngr card) {
        Map<String, Object> result = new HashMap<>();
        try {
            CardMngr.CPLC cplc = card.getCPLC();
            if (!cplc.values().isEmpty()) {
                for (Map.Entry<CardMngr.CPLC.Field, byte[]> entry : cplc.values().entrySet()) {
                    CardMngr.CPLC.Field field = entry.getKey();
                    byte[] value = entry.getValue();
                    result.put(field.name(), ByteUtil.bytesToHex(value, false));
                }
            }
        } catch (CardException ignored) {
        }
        return result;
    }

    @Override
    protected Map<String, Object> deviceObject(TestSuite suite) {
        if (suite instanceof CardTestSuite) {
            CardTestSuite cardSuite = (CardTestSuite) suite;
            Map<String, Object> result = new HashMap<>();
            result.put("type", "card");
            result.put("cplc", cplcObject(cardSuite.getCard()));
            result.put("ATR", ByteUtil.bytesToHex(cardSuite.getCard().getATR().getBytes(), false));
            return result;
        }
        return null;
    }
}
