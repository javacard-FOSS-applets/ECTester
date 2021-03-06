package cz.crcs.ectester.standalone.output;

import cz.crcs.ectester.common.output.BaseXMLTestWriter;
import cz.crcs.ectester.common.test.TestSuite;
import cz.crcs.ectester.common.test.Testable;
import cz.crcs.ectester.common.util.ByteUtil;
import cz.crcs.ectester.standalone.test.KeyAgreementTestable;
import cz.crcs.ectester.standalone.test.KeyGeneratorTestable;
import cz.crcs.ectester.standalone.test.SignatureTestable;
import org.w3c.dom.Element;

import javax.xml.parsers.ParserConfigurationException;
import java.io.OutputStream;
import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * @author Jan Jancar johny@neuromancer.sk
 */
public class XMLTestWriter extends BaseXMLTestWriter {

    public XMLTestWriter(OutputStream output) throws ParserConfigurationException {
        super(output);
    }

    private Element pkeyElement(PublicKey pkey) {
        Element pubkey = doc.createElement("pubkey");
        if (pkey == null) {
            return pubkey;
        }
        pubkey.setAttribute("algorithm", pkey.getAlgorithm());
        pubkey.setAttribute("format", pkey.getFormat());
        pubkey.setTextContent(ByteUtil.bytesToHex(pkey.getEncoded()));
        return pubkey;
    }

    private Element skeyElement(PrivateKey skey) {
        Element privkey = doc.createElement("privkey");
        if (skey == null) {
            return privkey;
        }
        privkey.setAttribute("algorithm", skey.getAlgorithm());
        privkey.setAttribute("format", skey.getFormat());
        privkey.setTextContent(ByteUtil.bytesToHex(skey.getEncoded()));
        return privkey;
    }

    private Element kaElement(KeyAgreementTestable kat) {
        Element katElem = doc.createElement("key-agreement");
        katElem.setAttribute("algo", kat.getKa().getAlgorithm());

        Element secret = doc.createElement("secret");
        secret.setTextContent(ByteUtil.bytesToHex(kat.getSecret()));
        katElem.appendChild(secret);

        PublicKey pkey = kat.getPublicKey();
        Element pubkey = pkeyElement(pkey);
        katElem.appendChild(pubkey);

        PrivateKey skey = kat.getPrivateKey();
        Element privkey = skeyElement(skey);
        katElem.appendChild(privkey);

        return katElem;
    }

    private Element kgtElement(KeyGeneratorTestable kgt) {
        Element kgtElem = doc.createElement("key-pair-generator");
        kgtElem.setAttribute("algo", kgt.getKpg().getAlgorithm());

        Element keyPair = doc.createElement("key-pair");
        if (kgt.getKeyPair() != null) {
            PublicKey pkey = kgt.getKeyPair().getPublic();
            Element pubkey = pkeyElement(pkey);
            keyPair.appendChild(pubkey);

            PrivateKey skey = kgt.getKeyPair().getPrivate();
            Element privkey = skeyElement(skey);
            keyPair.appendChild(privkey);
        }

        kgtElem.appendChild(keyPair);
        return kgtElem;
    }

    private Element sigElement(SignatureTestable sig) {
        Element sigElem = doc.createElement("signature");
        sigElem.setAttribute("verified", sig.getVerified() ? "true" : "false");
        sigElem.setAttribute("algo", sig.getSig().getAlgorithm());

        Element raw = doc.createElement("raw");
        raw.setTextContent(ByteUtil.bytesToHex(sig.getSignature()));
        sigElem.appendChild(raw);

        return sigElem;
    }

    @Override
    protected Element testableElement(Testable t) {
        Element result = doc.createElement("test");
        if (t instanceof KeyGeneratorTestable) {
            result.setAttribute("type", "key-pair-generator");
            result.appendChild(kgtElement((KeyGeneratorTestable) t));
        } else if (t instanceof KeyAgreementTestable) {
            result.setAttribute("type", "key-agreement");
            result.appendChild(kaElement((KeyAgreementTestable) t));
        } else if (t instanceof SignatureTestable) {
            result.setAttribute("type", "signature");
            result.appendChild(sigElement((SignatureTestable) t));
        }
        return result;
    }

    @Override
    protected Element deviceElement(TestSuite suite) {
        //TODO
        return null;
    }
}
