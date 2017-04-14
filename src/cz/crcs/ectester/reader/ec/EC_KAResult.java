package cz.crcs.ectester.reader.ec;

import cz.crcs.ectester.applet.EC_Consts;

/**
 * @author Jan Jancar johny@neuromancer.sk
 */
public class EC_KAResult extends EC_Data {

    private byte ka;
    private String curve;
    private String oneKey;
    private String otherKey;

    private String desc;

    public EC_KAResult(byte ka, String curve, String oneKey, String otherKey) {
        super(1);
        this.ka = ka;
        this.curve = curve;
        this.oneKey = oneKey;
        this.otherKey = otherKey;
    }

    public EC_KAResult(byte ka, String curve, String oneKey, String otherKey, String desc) {
        this(ka, curve, oneKey, otherKey);
        this.desc = desc;
    }

    public byte getKA() {
        return ka;
    }

    public String getCurve() {
        return curve;
    }

    public String getOneKey() {
        return oneKey;
    }

    public String getOtherKey() {
        return otherKey;
    }

    public String getDesc() {
        return desc;
    }

    @Override
    public String toString() {
        String agreement = ka == EC_Consts.KA_ECDH ? "ECDH" : "ECDHC";
        return agreement + " result over " + curve + ", " + oneKey + " + " + otherKey + (desc == null ? "" : ": " + desc);
    }

}
