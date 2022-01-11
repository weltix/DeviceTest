package ua.com.ekka.devicetest.eth;

public class IP_Settings {

    private String ip, netmask, gateway, dns;

    public IP_Settings() {
        ip = "";
        netmask = "";
        gateway = "";
        dns = "";
    }

    public IP_Settings(String ip, String netmask, String gateway, String dns) {
        this();
        this.ip = ip;
        this.netmask = netmask;
        this.gateway = gateway;
    }

    public void setIp(String ipAddress) {
        ip = ipAddress;
    }

    public void setNetmask(String netmask) {
        this.netmask = netmask;
    }

    public void setGateway(String gateway) {
        this.gateway = gateway;
    }


    public String getIp() {
        return this.ip;
    }

    public String getNetmask() {
        return this.netmask;
    }

    public String getGateway() {
        return this.gateway;
    }

    @Override
    public String toString() {
        return "IP_Settings{" +
                "ip=" + ip
                + ", netmask=" + netmask
                + ", gateway=" + gateway
                + '}';
    }
}
