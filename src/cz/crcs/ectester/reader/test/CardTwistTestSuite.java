package cz.crcs.ectester.reader.test;

import cz.crcs.ectester.applet.ECTesterApplet;
import cz.crcs.ectester.applet.EC_Consts;
import cz.crcs.ectester.common.ec.EC_Curve;
import cz.crcs.ectester.common.ec.EC_Key;
import cz.crcs.ectester.common.output.TestWriter;
import cz.crcs.ectester.common.test.CompoundTest;
import cz.crcs.ectester.common.test.Result;
import cz.crcs.ectester.common.test.Test;
import cz.crcs.ectester.data.EC_Store;
import cz.crcs.ectester.reader.CardMngr;
import cz.crcs.ectester.reader.ECTesterReader;
import cz.crcs.ectester.reader.command.Command;
import javacard.security.KeyPair;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author Jan Jancar johny@neuromancer.sk
 */
public class CardTwistTestSuite extends CardTestSuite {
    public CardTwistTestSuite(TestWriter writer, ECTesterReader.Config cfg, CardMngr cardManager) {
        super(writer, cfg, cardManager, "twist", "The twist test suite tests whether the card correctly rejects points on the quadratic twist of the curve during ECDH.");
    }

    @Override
    protected void runTests() throws Exception {
        Map<String, EC_Key.Public> pubkeys = EC_Store.getInstance().getObjects(EC_Key.Public.class, "twist");
        Map<EC_Curve, List<EC_Key.Public>> curves = new HashMap<>();
        for (EC_Key.Public key : pubkeys.values()) {
            EC_Curve curve = EC_Store.getInstance().getObject(EC_Curve.class, key.getCurve());
            if (curve.getBits() != cfg.bits && !cfg.all) {
                continue;
            }
            if (curve.getField() == KeyPair.ALG_EC_FP && !cfg.primeField || curve.getField() == KeyPair.ALG_EC_F2M && !cfg.binaryField) {
                continue;
            }
            List<EC_Key.Public> keys = curves.getOrDefault(curve, new LinkedList<>());
            keys.add(key);
            curves.putIfAbsent(curve, keys);
        }
        for (Map.Entry<EC_Curve, List<EC_Key.Public>> e : curves.entrySet()) {
            EC_Curve curve = e.getKey();
            List<EC_Key.Public> keys = e.getValue();

            doTest(CommandTest.expect(new Command.Allocate(this.card, ECTesterApplet.KEYPAIR_BOTH, curve.getBits(), curve.getField()), Result.ExpectedValue.SUCCESS));
            doTest(CommandTest.expect(new Command.Set(this.card, ECTesterApplet.KEYPAIR_BOTH, EC_Consts.CURVE_external, curve.getParams(), curve.flatten()), Result.ExpectedValue.SUCCESS));
            doTest(CommandTest.expect(new Command.Generate(this.card, ECTesterApplet.KEYPAIR_LOCAL), Result.ExpectedValue.SUCCESS));
            List<Test> ecdhTests = new LinkedList<>();
            for (EC_Key.Public pub : keys) {
                Command ecdhCommand = new Command.ECDH_direct(this.card, ECTesterApplet.KEYPAIR_LOCAL, ECTesterApplet.EXPORT_FALSE, EC_Consts.CORRUPTION_NONE, ECTesterApplet.KeyAgreement_ALG_EC_SVDP_DH, pub.flatten());
                ecdhTests.add(CommandTest.expect(ecdhCommand, Result.ExpectedValue.FAILURE, "Card correctly rejected point on twist.", "Card incorrectly accepted point on twist."));
            }
            doTest(CompoundTest.all(Result.ExpectedValue.SUCCESS, "Twist test of " + curve.getId(), ecdhTests.toArray(new Test[0])));
            new Command.Cleanup(this.card).send();
        }
    }
}
