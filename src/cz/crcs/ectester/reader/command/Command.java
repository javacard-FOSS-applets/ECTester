package cz.crcs.ectester.reader.command;

import cz.crcs.ectester.applet.ECTesterApplet;
import cz.crcs.ectester.applet.EC_Consts;
import cz.crcs.ectester.common.util.ByteUtil;
import cz.crcs.ectester.data.EC_Store;
import cz.crcs.ectester.reader.CardMngr;
import cz.crcs.ectester.reader.ECTesterReader;
import cz.crcs.ectester.reader.response.Response;
import cz.crcs.ectester.common.ec.EC_Curve;
import cz.crcs.ectester.common.ec.EC_Key;
import cz.crcs.ectester.common.ec.EC_Keypair;
import cz.crcs.ectester.common.ec.EC_Params;
import javacard.security.KeyPair;

import javax.smartcardio.CardException;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Jan Jancar johny@neuromancer.sk
 */
public abstract class Command {
    CommandAPDU cmd;
    CardMngr cardManager;

    Command(CardMngr cardManager) {
        this.cardManager = cardManager;
    }

    public CommandAPDU getAPDU() {
        return cmd;
    }

    public abstract Response send() throws CardException;

    public static List<Response> sendAll(List<Command> commands) throws CardException {
        List<Response> result = new ArrayList<>();
        for (Command cmd : commands) {
            result.add(cmd.send());
        }
        return result;
    }


    /**
     * @param keyPair   which keyPair/s (local/remote) to set curve domain parameters on
     * @param keyLength key length to choose
     * @param keyClass  key class to choose
     * @return a Command to send in order to prepare the curve on the keypairs.
     * @throws IOException if curve file cannot be found/opened
     */
    public static Command prepareCurve(CardMngr cardManager, EC_Store dataStore, ECTesterReader.Config cfg, byte keyPair, short keyLength, byte keyClass) throws IOException {

        if (cfg.customCurve) {
            // Set custom curve (one of the SECG curves embedded applet-side)
            short domainParams = keyClass == KeyPair.ALG_EC_FP ? EC_Consts.PARAMETERS_DOMAIN_FP : EC_Consts.PARAMETERS_DOMAIN_F2M;
            return new Command.Set(cardManager, keyPair, EC_Consts.getCurve(keyLength, keyClass), domainParams, null);
        } else if (cfg.namedCurve != null) {
            // Set a named curve.
            // parse cfg.namedCurve -> cat / id | cat | id
            EC_Curve curve = dataStore.getObject(EC_Curve.class, cfg.namedCurve);
            if (curve == null) {
                throw new IOException("Curve could no be found.");
            }
            if (curve.getBits() != keyLength) {
                throw new IOException("Curve bits mismatch: " + curve.getBits() + " vs " + keyLength + " entered.");
            }
            if (curve.getField() != keyClass) {
                throw new IOException("Curve field mismatch.");
            }

            byte[] external = curve.flatten();
            if (external == null) {
                throw new IOException("Couldn't read named curve data.");
            }
            return new Command.Set(cardManager, keyPair, EC_Consts.CURVE_external, curve.getParams(), external);
        } else if (cfg.curveFile != null) {
            // Set curve loaded from a file
            EC_Curve curve = new EC_Curve(null, keyLength, keyClass);

            FileInputStream in = new FileInputStream(cfg.curveFile);
            curve.readCSV(in);
            in.close();

            byte[] external = curve.flatten();
            if (external == null) {
                throw new IOException("Couldn't read the curve file correctly.");
            }
            return new Command.Set(cardManager, keyPair, EC_Consts.CURVE_external, curve.getParams(), external);
        } else {
            // Set default curve
            /* This command was generally causing problems for simulating on jcardsim.
             * Since there, .clearKey() resets all the keys values, even the domain.
             * This might break some other stuff.. But should not.
             */
            //commands.add(new Command.Clear(cardManager, keyPair));
            return null;
        }
    }


    /**
     * @param keyPair which keyPair/s to set the key params on
     * @return a CommandAPDU setting params loaded on the keyPair/s
     * @throws IOException if any of the key files cannot be found/opened
     */
    public static Command prepareKey(CardMngr cardManager, EC_Store dataStore, ECTesterReader.Config cfg, byte keyPair) throws IOException {
        short params = EC_Consts.PARAMETERS_NONE;
        byte[] data = null;

        if (cfg.key != null || cfg.namedKey != null) {
            params |= EC_Consts.PARAMETERS_KEYPAIR;
            EC_Params keypair;
            if (cfg.key != null) {
                keypair = new EC_Params(EC_Consts.PARAMETERS_KEYPAIR);

                FileInputStream in = new FileInputStream(cfg.key);
                keypair.readCSV(in);
                in.close();
            } else {
                keypair = dataStore.getObject(EC_Keypair.class, cfg.namedKey);
            }

            data = keypair.flatten();
            if (data == null) {
                throw new IOException("Couldn't read the key file correctly.");
            }
        }

        if (cfg.publicKey != null || cfg.namedPublicKey != null) {
            params |= EC_Consts.PARAMETER_W;
            EC_Params pub;
            if (cfg.publicKey != null) {
                pub = new EC_Params(EC_Consts.PARAMETER_W);

                FileInputStream in = new FileInputStream(cfg.publicKey);
                pub.readCSV(in);
                in.close();
            } else {
                pub = dataStore.getObject(EC_Key.Public.class, cfg.namedPublicKey);
                if (pub == null) {
                    pub = dataStore.getObject(EC_Keypair.class, cfg.namedPublicKey);
                }
            }

            byte[] pubkey = pub.flatten(EC_Consts.PARAMETER_W);
            if (pubkey == null) {
                throw new IOException("Couldn't read the public key file correctly.");
            }
            data = pubkey;
        }
        if (cfg.privateKey != null || cfg.namedPrivateKey != null) {
            params |= EC_Consts.PARAMETER_S;
            EC_Params priv;
            if (cfg.privateKey != null) {
                priv = new EC_Params(EC_Consts.PARAMETER_S);

                FileInputStream in = new FileInputStream(cfg.privateKey);
                priv.readCSV(in);
                in.close();
            } else {
                priv = dataStore.getObject(EC_Key.Private.class, cfg.namedPrivateKey);
                if (priv == null) {
                    priv = dataStore.getObject(EC_Keypair.class, cfg.namedPrivateKey);
                }
            }

            byte[] privkey = priv.flatten(EC_Consts.PARAMETER_S);
            if (privkey == null) {
                throw new IOException("Couldn't read the private key file correctly.");
            }
            data = ByteUtil.concatenate(data, privkey);
        }
        return new Command.Set(cardManager, keyPair, EC_Consts.CURVE_external, params, data);
    }


    /**
     *
     */
    public static class Allocate extends Command {
        private byte keyPair;
        private short keyLength;
        private byte keyClass;

        /**
         * Creates the INS_ALLOCATE instruction.
         *
         * @param cardManager cardManager to send APDU through
         * @param keyPair     which keyPair to use, local/remote (KEYPAIR_* | ...)
         * @param keyLength   key length to set
         * @param keyClass    key class to allocate
         */
        public Allocate(CardMngr cardManager, byte keyPair, short keyLength, byte keyClass) {
            super(cardManager);
            this.keyPair = keyPair;
            this.keyLength = keyLength;
            this.keyClass = keyClass;

            byte[] data = new byte[]{0, 0, keyClass};
            ByteUtil.setShort(data, 0, keyLength);
            this.cmd = new CommandAPDU(ECTesterApplet.CLA_ECTESTERAPPLET, ECTesterApplet.INS_ALLOCATE, keyPair, 0x00, data);
        }

        @Override
        public Response.Allocate send() throws CardException {
            long elapsed = -System.nanoTime();
            ResponseAPDU response = cardManager.send(cmd);
            elapsed += System.nanoTime();
            return new Response.Allocate(response, elapsed, keyPair, keyLength, keyClass);
        }

        @Override
        public String toString() {
            return "Allocate";
        }
    }

    /**
     *
     */
    public static class AllocateKeyAgreement extends Command {
        private byte kaType;

        /**
         * Creates the INS_ALLOCATE_KA instruction.
         *
         * @param cardManager cardManager to send APDU through
         * @param kaType which type of KeyAgreement to use
         */
        public AllocateKeyAgreement(CardMngr cardManager, byte kaType) {
            super(cardManager);
            this.kaType = kaType;
            byte[] data = new byte[]{kaType};
            this.cmd = new CommandAPDU(ECTesterApplet.CLA_ECTESTERAPPLET, ECTesterApplet.INS_ALLOCATE_KA, 0x00, 0x00, data);
        }

        @Override
        public Response.AllocateKeyAgreement send() throws CardException {
            long elapsed = -System.nanoTime();
            ResponseAPDU response = cardManager.send(cmd);
            elapsed += System.nanoTime();
            return new Response.AllocateKeyAgreement(response, elapsed, kaType);
        }

        @Override
        public String toString() {
            return "AllocateKeyAgreement";
        }
    }

    /**
     *
     */
    public static class AllocateSignature extends Command {
        private byte sigType;

        /**
         * Creates the INS_ALLOCATE_SIG instruction.
         *
         * @param cardManager cardManager to send APDU through
         * @param sigType which type of Signature to use
         */
        public AllocateSignature(CardMngr cardManager, byte sigType) {
            super(cardManager);
            this.sigType = sigType;
            byte[] data = new byte[]{sigType};
            this.cmd = new CommandAPDU(ECTesterApplet.CLA_ECTESTERAPPLET, ECTesterApplet.INS_ALLOCATE_SIG, 0x00, 0x00, data);
        }

        @Override
        public Response.AllocateSignature send() throws CardException {
            long elapsed = -System.nanoTime();
            ResponseAPDU response = cardManager.send(cmd);
            elapsed += System.nanoTime();
            return new Response.AllocateSignature(response, elapsed, sigType);
        }

        @Override
        public String toString() {
            return "AllocateSignature";
        }
    }

    /**
     *
     */
    public static class Clear extends Command {
        private byte keyPair;

        /**
         * @param cardManager cardManager to send APDU through
         * @param keyPair     which keyPair clear, local/remote (KEYPAIR_* || ...)
         */
        public Clear(CardMngr cardManager, byte keyPair) {
            super(cardManager);
            this.keyPair = keyPair;

            this.cmd = new CommandAPDU(ECTesterApplet.CLA_ECTESTERAPPLET, ECTesterApplet.INS_CLEAR, keyPair, 0x00);
        }

        @Override
        public Response.Clear send() throws CardException {
            long elapsed = -System.nanoTime();
            ResponseAPDU response = cardManager.send(cmd);
            elapsed += System.nanoTime();
            return new Response.Clear(response, elapsed, keyPair);
        }

        @Override
        public String toString() {
            return "Clear";
        }
    }

    /**
     *
     */
    public static class Set extends Command {
        private byte keyPair;
        private byte curve;
        private short params;
        private byte[] external;

        /**
         * Creates the INS_SET instruction.
         *
         * @param cardManager cardManager to send APDU through
         * @param keyPair     which keyPair to set params on, local/remote (KEYPAIR_* || ...)
         * @param curve       curve to set (EC_Consts.CURVE_*)
         * @param params      parameters to set (EC_Consts.PARAMETER_* | ...)
         * @param external    external curve data, can be null
         */
        public Set(CardMngr cardManager, byte keyPair, byte curve, short params, byte[] external) {
            super(cardManager);
            this.keyPair = keyPair;
            this.curve = curve;
            this.params = params;
            this.external = external;

            int len = external != null ? 2 + external.length : 2;
            byte[] data = new byte[len];
            ByteUtil.setShort(data, 0, params);
            if (external != null) {
                System.arraycopy(external, 0, data, 2, external.length);
            }

            this.cmd = new CommandAPDU(ECTesterApplet.CLA_ECTESTERAPPLET, ECTesterApplet.INS_SET, keyPair, curve, data);
        }

        @Override
        public Response.Set send() throws CardException {
            long elapsed = -System.nanoTime();
            ResponseAPDU response = cardManager.send(cmd);
            elapsed += System.nanoTime();
            return new Response.Set(response, elapsed, keyPair, curve, params);
        }

        @Override
        public String toString() {
            return "Set";
        }
    }

    /**
     *
     */
    public static class Corrupt extends Command {
        private byte keyPair;
        private byte key;
        private short params;
        private byte corruption;

        /**
         * @param cardManager cardManager to send APDU through
         * @param keyPair     which keyPair to corrupt, local/remote (KEYPAIR_* || ...)
         * @param key         key to corrupt (EC_Consts.KEY_* | ...)
         * @param params      parameters to corrupt (EC_Consts.PARAMETER_* | ...)
         * @param corruption  corruption type (EC_Consts.CORRUPTION_*)
         */
        public Corrupt(CardMngr cardManager, byte keyPair, byte key, short params, byte corruption) {
            super(cardManager);
            this.keyPair = keyPair;
            this.key = key;
            this.params = params;
            this.corruption = corruption;

            byte[] data = new byte[3];
            ByteUtil.setShort(data, 0, params);
            data[2] = corruption;

            this.cmd = new CommandAPDU(ECTesterApplet.CLA_ECTESTERAPPLET, ECTesterApplet.INS_CORRUPT, keyPair, key, data);
        }

        @Override
        public Response.Corrupt send() throws CardException {
            long elapsed = -System.nanoTime();
            ResponseAPDU response = cardManager.send(cmd);
            elapsed += System.nanoTime();
            return new Response.Corrupt(response, elapsed, keyPair, key, params, corruption);
        }

        @Override
        public String toString() {
            return "Corrupt";
        }
    }

    /**
     *
     */
    public static class Generate extends Command {
        private byte keyPair;

        /**
         * Creates the INS_GENERATE instruction.
         *
         * @param cardManager cardManager to send APDU through
         * @param keyPair     which keyPair to generate, local/remote (KEYPAIR_* || ...)
         */
        public Generate(CardMngr cardManager, byte keyPair) {
            super(cardManager);
            this.keyPair = keyPair;

            this.cmd = new CommandAPDU(ECTesterApplet.CLA_ECTESTERAPPLET, ECTesterApplet.INS_GENERATE, keyPair, 0);
        }

        @Override
        public Response.Generate send() throws CardException {
            long elapsed = -System.nanoTime();
            ResponseAPDU response = cardManager.send(cmd);
            elapsed += System.nanoTime();
            return new Response.Generate(response, elapsed, keyPair);
        }

        @Override
        public String toString() {
            return "Generate";
        }
    }

    /**
     *
     */
    public static class Export extends Command {
        private byte keyPair;
        private byte key;
        private short params;

        /**
         * Creates the INS_EXPORT instruction.
         *
         * @param cardManager cardManager to send APDU through
         * @param keyPair     keyPair to export from (KEYPAIR_* | ...)
         * @param key         key to export from (EC_Consts.KEY_* | ...)
         * @param params      params to export (EC_Consts.PARAMETER_* | ...)
         */
        public Export(CardMngr cardManager, byte keyPair, byte key, short params) {
            super(cardManager);
            this.keyPair = keyPair;
            this.key = key;
            this.params = params;

            byte[] data = new byte[2];
            ByteUtil.setShort(data, 0, params);

            this.cmd = new CommandAPDU(ECTesterApplet.CLA_ECTESTERAPPLET, ECTesterApplet.INS_EXPORT, keyPair, key, data);
        }

        @Override
        public Response.Export send() throws CardException {
            long elapsed = -System.nanoTime();
            ResponseAPDU response = cardManager.send(cmd);
            elapsed += System.nanoTime();
            return new Response.Export(response, elapsed, keyPair, key, params);
        }

        @Override
        public String toString() {
            return "Export";
        }
    }

    /**
     *
     */
    public static class ECDH extends Command {
        private byte pubkey;
        private byte privkey;
        private byte export;
        private short corruption;
        private byte type;

        /**
         * Creates the INS_ECDH instruction.
         *
         * @param cardManager cardManager to send APDU through
         * @param pubkey      keyPair to use for public key, (KEYPAIR_LOCAL || KEYPAIR_REMOTE)
         * @param privkey     keyPair to use for private key, (KEYPAIR_LOCAL || KEYPAIR_REMOTE)
         * @param export      whether to export ECDH secret
         * @param corruption  whether to invalidate the pubkey before ECDH (EC_Consts.CORRUPTION_* | ...)
         * @param type        ECDH algorithm type (EC_Consts.KA_* | ...)
         */
        public ECDH(CardMngr cardManager, byte pubkey, byte privkey, byte export, short corruption, byte type) {
            super(cardManager);
            this.pubkey = pubkey;
            this.privkey = privkey;
            this.export = export;
            this.corruption = corruption;
            this.type = type;

            byte[] data = new byte[]{export, 0,0, type};
            ByteUtil.setShort(data, 1, corruption);

            this.cmd = new CommandAPDU(ECTesterApplet.CLA_ECTESTERAPPLET, ECTesterApplet.INS_ECDH, pubkey, privkey, data);
        }

        @Override
        public Response.ECDH send() throws CardException {
            long elapsed = -System.nanoTime();
            ResponseAPDU response = cardManager.send(cmd);
            elapsed += System.nanoTime();
            return new Response.ECDH(response, elapsed, pubkey, privkey, export, corruption, type);
        }

        @Override
        public String toString() {
            return "ECDH";
        }
    }

    /**
     *
     */
    public static class ECDH_direct extends Command {
        private byte privkey;
        private byte export;
        private short corruption;
        private byte type;
        private byte[] pubkey;

        /**
         * Creates the INS_ECDH_DIRECT instruction.
         *
         * @param cardManager cardManager to send APDU through
         * @param privkey     keyPair to use for private key, (KEYPAIR_LOCAL || KEYPAIR_REMOTE)
         * @param export      whether to export ECDH secret
         * @param corruption  whether to invalidate the pubkey before ECDH (EC_Consts.CORRUPTION_* | ...)
         * @param type        EC KeyAgreement type
         * @param pubkey      pubkey data to do ECDH with.
         */
        public ECDH_direct(CardMngr cardManager, byte privkey, byte export, short corruption, byte type, byte[] pubkey) {
            super(cardManager);
            this.privkey = privkey;
            this.export = export;
            this.corruption = corruption;
            this.type = type;
            this.pubkey = pubkey;

            byte[] data = new byte[3 + pubkey.length];
            ByteUtil.setShort(data, 0, corruption);
            data[2] = type;
            System.arraycopy(pubkey, 0, data, 3, pubkey.length);

            this.cmd = new CommandAPDU(ECTesterApplet.CLA_ECTESTERAPPLET, ECTesterApplet.INS_ECDH_DIRECT, privkey, export, data);
        }

        @Override
        public Response.ECDH send() throws CardException {
            long elapsed = -System.nanoTime();
            ResponseAPDU response = cardManager.send(cmd);
            elapsed += System.nanoTime();
            return new Response.ECDH(response, elapsed, ECTesterApplet.KEYPAIR_REMOTE, privkey, export, corruption, type);
        }

        @Override
        public String toString() {
            return "ECDH_direct";
        }
    }

    public static class ECDSA extends Command {
        private byte keyPair;
        private byte sigType;
        private byte export;
        private byte[] raw;

        /**
         * Creates the INS_ECDSA instruction.
         *
         * @param cardManager cardManager to send APDU through
         * @param keyPair     keyPair to use for signing and verification (KEYPAIR_LOCAL || KEYPAIR_REMOTE)
         * @param sigType     Signature type to use
         * @param export      whether to export ECDSA signature
         * @param raw         data to sign, can be null, in which case random data is signed.
         */
        public ECDSA(CardMngr cardManager, byte keyPair, byte sigType, byte export, byte[] raw) {
            super(cardManager);
            this.keyPair = keyPair;
            this.sigType = sigType;
            this.export = export;
            this.raw = raw;

            int len = raw != null ? raw.length : 0;
            byte[] data = new byte[3 + len];
            data[0] = sigType;
            ByteUtil.setShort(data, 1, (short) len);
            if (raw != null) {
                System.arraycopy(raw, 0, data, 3, len);
            }

            this.cmd = new CommandAPDU(ECTesterApplet.CLA_ECTESTERAPPLET, ECTesterApplet.INS_ECDSA, keyPair, export, data);
        }

        @Override
        public Response.ECDSA send() throws CardException {
            long elapsed = -System.nanoTime();
            ResponseAPDU response = cardManager.send(cmd);
            elapsed += System.nanoTime();
            return new Response.ECDSA(response, elapsed, keyPair, sigType, export, raw);
        }

        @Override
        public String toString() {
            return "ECDSA";
        }
    }

    /**
     *
     */
    public static class Cleanup extends Command {

        /**
         * @param cardManager cardManager to send APDU through
         */
        public Cleanup(CardMngr cardManager) {
            super(cardManager);

            this.cmd = new CommandAPDU(ECTesterApplet.CLA_ECTESTERAPPLET, ECTesterApplet.INS_CLEANUP, 0, 0);
        }

        @Override
        public Response.Cleanup send() throws CardException {
            long elapsed = -System.nanoTime();
            ResponseAPDU response = cardManager.send(cmd);
            elapsed += System.nanoTime();
            return new Response.Cleanup(response, elapsed);
        }

        @Override
        public String toString() {
            return "Cleanup";
        }
    }
}

