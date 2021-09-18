package org.mirai;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.console.plugin.jvm.JavaPlugin;
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescriptionBuilder;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.contact.Group;
import net.mamoe.mirai.event.GlobalEventChannel;
import net.mamoe.mirai.event.Listener;
import net.mamoe.mirai.event.events.GroupMessageEvent;
import net.mamoe.mirai.message.data.MessageChain;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public final class GetDailyReport extends JavaPlugin {
    public static final GetDailyReport INSTANCE = new GetDailyReport();
    Listener<GroupMessageEvent> listener;

    public class quartzJob implements Job {
        @Override
        public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
            sendDailyReportAll();
        }
    }

    private GetDailyReport() {
        super(new JvmPluginDescriptionBuilder("org.mirai.getDailyReport", "1.0")
                .name("getDailyReport")
                .info("Get daily report from soyiji.com.")
                .author("Jobove")
                .build());
    }

    @Override
    public void onEnable() {
        getLogger().info("GetDailyReport plugin loaded!");
        if (!checkFolder()) {
            return;
        }

        try {
            Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
            scheduler.start();
            JobDetail jobDetail = JobBuilder.newJob(quartzJob.class)
                    .withIdentity("getDailyReport", "jobGroup1")
                    .build();
            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity("trigger1", "triggerGroup1")
                    .withSchedule(
                            CronScheduleBuilder.cronSchedule("0 0 9 * * ?")
                    )
                    .build();
            scheduler.scheduleJob(jobDetail, trigger);
        } catch (SchedulerException se) {
            se.printStackTrace();
        }

        listener = GlobalEventChannel.INSTANCE.subscribeAlways(GroupMessageEvent.class,
                groupMessageEvent -> {
                    MessageChain chain = groupMessageEvent.getMessage();
                    String messageString = chain.contentToString();
                    String re = "^获取今日新闻$";
                    Pattern pattern = Pattern.compile(re, Pattern.MULTILINE);
                    Matcher matcher = pattern.matcher(messageString);

                    if (matcher.find()) {
//                        sendDailyReport(groupMessageEvent.getGroup());
                        sendDailyReportAll();
                    }
                });
    }

    public boolean checkFolder() {
        File dataFolder = new File("data/getDailyReport");
        if (dataFolder.exists() && dataFolder.isDirectory())
            return true;
        boolean result;
        result = dataFolder.mkdirs();
        if (!result) {
            getLogger().warning("Create folder failed! Plugin won't be working.");
        }
        return result;
    }

    public static String checkPhoto() {
        Date nowDate = new Date();
        SimpleDateFormat getYear = new SimpleDateFormat("yyyy"),
                getMonth = new SimpleDateFormat("MM"),
                getDay = new SimpleDateFormat("dd"),
                getHour = new SimpleDateFormat("HH");
        int year = Integer.parseInt(getYear.format(nowDate)),
                month = Integer.parseInt(getMonth.format(nowDate)),
                day = Integer.parseInt(getDay.format(nowDate)),
                hour = Integer.parseInt(getHour.format(nowDate));

        File imageFolder = new File("data/getDailyReport");
        File[] imageList = imageFolder.listFiles();

        String returnValue = "";

        if (imageList != null) {
            String regex = "[^-]*?-(\\d{4})-(\\d\\d?)-(\\d\\d?)\\.[a-zA-Z]{3}";
            Pattern pattern = Pattern.compile(regex);

            if (hour < 9) {
                --day;
            }

            for (File item : imageList) {
                if (item.isFile()) {
                    String fileName = item.getName();
                    Matcher matcher = pattern.matcher(fileName);
                    if (matcher.find()) {
                        int fileYear = Integer.parseInt(matcher.group(1)),
                                fileMonth = Integer.parseInt(matcher.group(2)),
                                fileDay = Integer.parseInt(matcher.group(3));
                        if (year == fileYear &&
                                month == fileMonth &&
                                day == fileDay) {
                            returnValue = "data/getDailyReport/" + fileName;
                        }
                    }
                }
            }
        }
        return returnValue;
    }

    public void sendDailyReportAll() {
        String photoPath = checkPhoto();
        boolean hasPhoto = photoPath.length() > 0;
        Bot myBot = Bot.getInstance(2261118466L);
        if (hasPhoto) {
            File photoFile = new File(photoPath);
            for (Group item : myBot.getGroups()) {
                Contact.sendImage(item, photoFile);
            }
        } else {
            File photoFile = new File(getPhoto());
            for (Group item : myBot.getGroups()) {
                Contact.sendImage(item, photoFile);
            }
        }
    }

    public void sendDailyReport(Group group) {
        String photoPath = checkPhoto();
        boolean hasPhoto = photoPath.length() > 0;
        File photoFile;
        if (hasPhoto) {
            photoFile = new File(photoPath);
        } else {
            photoFile = new File(getPhoto());
        }
        Contact.sendImage(group, photoFile);
    }

    public String getPhoto() {
        JSONObject jsonObject = JSON.parseObject(getHttpResult("http://api.soyiji.com/news_jpg"));
        String photoUrl = jsonObject.getString("url");
        return downloadPhoto(photoUrl);
    }

    public String downloadPhoto(String photoUrl) {
        HttpURLConnection connection = null;
        InputStream is = null;

        String regex = "[^/]*?\\.[a-zA-Z]{3}$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(photoUrl);
        String fileName = "";
        if (matcher.find()) {
            fileName = matcher.group(0);
        }

        File photoFile = new File("data/getDailyReport/" + fileName);
        if (!photoFile.exists()) {
            try {
                photoFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            URL url = new URL(photoUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(60000);
            connection.setRequestProperty("Referer", "safe.soyiji.com");
            connection.connect();

            if (connection.getResponseCode() == 200) {
                is = connection.getInputStream();
                OutputStream outputStream = new FileOutputStream(photoFile);
                int j;
                while ((j = is.read()) != -1) {
                    outputStream.write(j);
                }
                is.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return "data/getDailyReport/" + fileName;
    }

    public String getHttpResult(String queryUrl) {
        HttpURLConnection connection = null;
        InputStream is = null;
        BufferedReader br = null;
        String result = null;
        try {
            URL url = new URL(queryUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(60000);
            connection.connect();

            if (connection.getResponseCode() == 200) {
                is = connection.getInputStream();
                br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String tmp;
                while ((tmp = br.readLine()) != null) {
                    sb.append(tmp);
                    sb.append("\r\n");
                }
                result = sb.toString();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
        return result;
    }

}