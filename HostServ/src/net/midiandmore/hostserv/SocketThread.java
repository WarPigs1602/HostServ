/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package net.midiandmore.hostserv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.codec.digest.DigestUtils;

/**
 * Starts a new Thread
 *
 * @author Andreas Pschorn
 */
public class SocketThread implements Runnable, Software {

    /**
     * @return the hosts
     */
    public HashMap<String, String> getHosts() {
        return hosts;
    }

    /**
     * @param hosts the hosts to set
     */
    public void setHosts(HashMap<String, String> hosts) {
        this.hosts = hosts;
    }

    /**
     * @return the nick
     */
    public String getNick() {
        return nick;
    }

    /**
     * @param nick the nick to set
     */
    public void setNick(String nick) {
        this.nick = nick;
    }

    /**
     * @return the identd
     */
    public String getIdentd() {
        return identd;
    }

    /**
     * @param identd the identd to set
     */
    public void setIdentd(String identd) {
        this.identd = identd;
    }

    /**
     * @return the servername
     */
    public String getServername() {
        return servername;
    }

    /**
     * @param servername the servername to set
     */
    public void setServername(String servername) {
        this.servername = servername;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @return the ip
     */
    public byte[] getIp() {
        return ip;
    }

    /**
     * @param ip the ip to set
     */
    public void setIp(byte[] ip) {
        this.ip = ip;
    }

    private Thread thread;
    private HostServ mi;
    private Socket socket;
    private PrintWriter pw;
    private BufferedReader br;
    private boolean runs;
    private String serverNumeric;
    private String numeric;
    private String nick;
    private String identd;
    private String servername;
    private String description;
    private byte[] ip;
    private HashMap<String, String> nicks;
    private HashMap<String, String> hosts;
    private HashMap<String, String> accounts;
    private HashMap<String, String> x;
    private boolean reg;

    public SocketThread(HostServ mi) {
        setMi(mi);
        setNicks(new HashMap<>());
        setHosts(new HashMap<>());
        setAccounts(new HashMap<>());
        setX(new HashMap<>());
        setReg(false);
        (thread = new Thread(this)).start();
    }

    protected void handshake(String nick, String password, String servername, String description, String numeric, String identd) {
        System.out.println("Starting handshake...");
        sendText("PASS :%s", password);
        sendText("SERVER %s %d %d %d J10 %s]]] :%s", servername, 1, time(), time(), numeric, description);
        var ia = getSocket().getInetAddress().getHostAddress();
        var li = String.valueOf(ipToInt(ia)).getBytes();
        setServername(servername);
        setNick(nick);
        setIdentd(identd);
        setDescription(description);
        setIp(li);
        setNumeric(numeric);
        System.out.println("Registering nick: " + getNick());
        sendText("%s N %s 1 %d %s %s +oikr %s %sAAA :%s", getNumeric(), getNick(), time(), getIdentd(), getServername(), getNick(), getNumeric(), getDescription());
        sendText("%s EB", numeric);
    }

    /**
     * Turns an IP address into an integer and returns this
     *
     * @param addr
     * @return
     */
    private int ipToInt(String addr) {
        String[] addrArray = addr.split("\\.");
        int[] num = new int[]{
            Integer.parseInt(addrArray[0]),
            Integer.parseInt(addrArray[1]),
            Integer.parseInt(addrArray[2]),
            Integer.parseInt(addrArray[3])
        };

        int result = ((num[0] & 255) << 24);
        result = result | ((num[1] & 255) << 16);
        result = result | ((num[2] & 255) << 8);
        result = result | (num[3] & 255);
        return result;
    }

    protected void sendText(String text, Object... args) {
        getPw().println(text.formatted(args));
        getPw().flush();
        if (getMi().getConfig().getConfigFile().getProperty("debug", "false").equalsIgnoreCase("true")) {
            System.out.printf("DEBUG sendText: %s\n", text.formatted(args));
        }
    }

    protected String parseCloak(String host) {
        var sb = new StringBuilder();
        try {
            if (host.contains(":")) {
                var tokens = host.split(":");
                for (var elem : tokens) {
                    if (elem.isBlank()) {
                        continue;
                    }
                    sb.append(parse(elem));
                    sb.append(".");
                }
                sb.append("ip");

            } else {
                var add = InetAddress.getByName(host).getHostAddress();
                if (add.contains(".")) {
                    var tokens = add.split("\\.");
                    for (var elem : tokens) {
                        sb.append(parse(elem));
                        sb.append(".");
                    }
                    sb.append("ip");
                } else if (add.contains(":")) {
                    var tokens = add.split(":");
                    for (var elem : tokens) {
                        if (elem.isBlank() || elem.equals("0")) {
                            continue;
                        }
                        sb.append(parse(elem));
                        sb.append(".");
                    }
                    sb.append("ip");
                }
            }
        } catch (UnknownHostException ex) {
        }
        if (sb.isEmpty()) {
            sb.append(host);
        }
        return sb.toString();
    }

    private String parse(String text) {
        var buf = DigestUtils.sha256Hex(text).toCharArray();
        var sb = new StringBuilder();
        int i = 0;
        for (var chr : buf) {
            sb.append(chr);
            if (i >= 3) {
                break;
            }
            i++;
        }
        return sb.toString();
    }

    protected void parseLine(String text) {
        var p = getMi().getConfig().getConfigFile();
        text = text.trim();
        if (text.startsWith("SERVER")) {
            setServerNumeric(text.split(" ")[6].substring(0, 1));
            System.out.println("Getting SERVER response...");
        } else if (getServerNumeric() != null) {
            var elem = text.split(" ");
            if (elem[1].equals("N") && elem.length > 4) {
                var priv = elem[7].contains("r");
                var hidden = elem[7].contains("h");
                var x = elem[7].contains("x");
                String acc = null;
                String nick = null;
                if (elem[8].contains(":")) {
                    acc = elem[8].split(":", 2)[0];
                    if (hidden) {
                        nick = elem[11];
                    } else {
                        nick = elem[10];
                        sendText("%s SH %s %s %s", getNumeric(), nick, elem[5], parseCloak(elem[6]));
                    }
                    if (x) {
                        sendText("%s SH %s %s %s", getNumeric(), nick, elem[5], elem[2] + getMi().getConfig().getConfigFile().getProperty("reg_host"));
                    }
                } else {
                    acc = "";
                    if (hidden) {
                        nick = elem[10];
                    } else {
                        nick = elem[9];
                        sendText("%s SH %s %s %s", getNumeric(), nick, elem[5], parseCloak(elem[6]));
                    }
                }
                getAccounts().put(nick, acc);
                getNicks().put(nick, elem[2]);
                getHosts().put(nick, elem[5] + "@" + elem[6]);
                getX().put(nick, x ? "true" : "false");

            } else if (elem[1].equals("N") && elem.length == 4) {
                getNicks().replace(elem[0], elem[2]);
            } else if (elem[1].equals("M") && elem.length == 4) {
                var nick = elem[0];
                if (elem[3].contains("x")) {
                    getX().replace(nick, "true");
                }
                if (elem[3].contains("x") && getNicks().get(nick).equalsIgnoreCase(elem[2]) && !getAccounts().get(nick).isBlank()) {
                    var host = getHosts().get(nick);
                    sendText("%s SH %s %s %s", getNumeric(), nick, host.split("@")[0], elem[2] + getMi().getConfig().getConfigFile().getProperty("reg_host"));
                }
            } else if (elem[1].equals("AC")) {
                var acc = elem[3];
                var nick = elem[2];
                if (getX().get(nick).equals("true")) {
                    var host = getHosts().get(nick);
                    sendText("%s SH %s %s %s", getNumeric(), nick, host.split("@")[0], acc + getMi().getConfig().getConfigFile().getProperty("reg_host"));
                }
                if (getAccounts().containsKey(nick)) {
                    getAccounts().replace(nick, acc);
                } else {
                    getAccounts().put(nick, acc);
                }
            } else if (elem[1].equals("EB")) {
                sendText("%s EA", getNumeric());
                System.out.println("Handshake complete...");
                System.out.println("Joining 1 channel...");
                joinChannel("#twilightzone");
                System.out.println("Channels joined...");
                System.out.println("Successfully connected...");
            } else if (elem[1].equals("G")) {
                sendText("%s Z %s", getNumeric(), text.substring(5));
            } else if (elem[1].equals("Q")) {
                var nick = elem[0];
                getAccounts().remove(nick);
                getNicks().remove(nick);
                getHosts().remove(nick);
                getX().remove(nick);
            } else if (elem[1].equals("D")) {
                var nick = elem[2];
                getAccounts().remove(nick);
                getNicks().remove(nick);
                getHosts().remove(nick);
                getX().remove(nick);
            }
        }
    }

    private void joinChannel(String channel) {
        sendText("%sAAA J %s", getNumeric(), channel);
        sendText("%s M %s +o %sAAA", getNumeric(), channel, getNumeric());
    }

    private long time() {
        return System.currentTimeMillis() / 1000;
    }

    @Override
    public void run() {
        System.out.println("Connecting to server...");
        setRuns(true);
        var host = getMi().getConfig().getConfigFile().getProperty("host");
        var port = getMi().getConfig().getConfigFile().getProperty("port");
        var password = getMi().getConfig().getConfigFile().getProperty("password");
        var nick = getMi().getConfig().getConfigFile().getProperty("nick");
        var servername = getMi().getConfig().getConfigFile().getProperty("servername");
        var description = getMi().getConfig().getConfigFile().getProperty("description");
        var numeric = getMi().getConfig().getConfigFile().getProperty("numeric");
        var identd = getMi().getConfig().getConfigFile().getProperty("identd");
        try {
            setSocket(new Socket(host, Integer.parseInt(port)));
            setPw(new PrintWriter(getSocket().getOutputStream()));
            setBr(new BufferedReader(new InputStreamReader(getSocket().getInputStream())));
            var content = "";
            handshake(nick, password, servername, description, numeric, identd);
            while (!getSocket().isClosed() && (content = getBr().readLine()) != null && isRuns()) {
                parseLine(content);
                if (getMi().getConfig().getConfigFile().getProperty("debug", "false").equalsIgnoreCase("true")) {
                    System.out.printf("DEBUG get text: %s\n", content);
                }
            }
        } catch (IOException | NumberFormatException ex) {
        }
        if (getPw() != null) {
            try {
                getPw().close();
            } catch (Exception ex) {
            }
        }
        if (getBr() != null) {
            try {
                getBr().close();
            } catch (IOException ex) {
            }
        }
        if (getSocket() != null && !getSocket().isClosed()) {
            try {
                getSocket().close();
            } catch (IOException ex) {
            }
        }
        setPw(null);
        setBr(null);
        setSocket(null);
        setRuns(false);
        System.out.println("Disconnected...");
    }

    /**
     * @return the mi
     */
    public HostServ getMi() {
        return mi;
    }

    /**
     * @param mi the mi to set
     */
    public void setMi(HostServ mi) {
        this.mi = mi;
    }

    /**
     * @return the socket
     */
    public Socket getSocket() {
        return socket;
    }

    /**
     * @param socket the socket to set
     */
    public void setSocket(Socket socket) {
        this.socket = socket;
    }

    /**
     * @return the pw
     */
    public PrintWriter getPw() {
        return pw;
    }

    /**
     * @param pw the pw to set
     */
    public void setPw(PrintWriter pw) {
        this.pw = pw;
    }

    /**
     * @return the br
     */
    public BufferedReader getBr() {
        return br;
    }

    /**
     * @param br the br to set
     */
    public void setBr(BufferedReader br) {
        this.br = br;
    }

    /**
     * @return the runs
     */
    public boolean isRuns() {
        return runs;
    }

    /**
     * @param runs the runs to set
     */
    public void setRuns(boolean runs) {
        this.runs = runs;
    }

    /**
     * @return the serverNumeric
     */
    public String getServerNumeric() {
        return serverNumeric;
    }

    /**
     * @param serverNumeric the serverNumeric to set
     */
    public void setServerNumeric(String serverNumeric) {
        this.serverNumeric = serverNumeric;
    }

    /**
     * @return the numeric
     */
    public String getNumeric() {
        return numeric;
    }

    /**
     * @param numeric the numeric to set
     */
    public void setNumeric(String numeric) {
        this.numeric = numeric;
    }

    /**
     * @return the reg
     */
    public boolean isReg() {
        return reg;
    }

    /**
     * @param reg the reg to set
     */
    public void setReg(boolean reg) {
        this.reg = reg;
    }

    /**
     * @return the nicks
     */
    public HashMap<String, String> getNicks() {
        return nicks;
    }

    /**
     * @param nicks the nicks to set
     */
    public void setNicks(HashMap<String, String> nicks) {
        this.nicks = nicks;
    }

    /**
     * @return the accounts
     */
    public HashMap<String, String> getAccounts() {
        return accounts;
    }

    /**
     * @param accounts the accounts to set
     */
    public void setAccounts(HashMap<String, String> accounts) {
        this.accounts = accounts;
    }

    /**
     * @return the x
     */
    public HashMap<String, String> getX() {
        return x;
    }

    /**
     * @param x the x to set
     */
    public void setX(HashMap<String, String> x) {
        this.x = x;
    }
}
