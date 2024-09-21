package org.example;

import java.time.Duration;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import io.github.bonigarcia.wdm.WebDriverManager;
import net.lightbody.bmp.BrowserMobProxy;
import net.lightbody.bmp.BrowserMobProxyServer;
import net.lightbody.bmp.client.ClientUtil;
import net.lightbody.bmp.core.har.Har;
import net.lightbody.bmp.proxy.CaptureType;

public class TenderfyTest {

    private static final Logger logger = LoggerFactory.getLogger(TenderfyTest.class);

    static {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        
        // Bỏ qua log của Selenium (Optional)
        ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(Level.ERROR);
        
        // Set logger của test class ở mức INFO 
        ch.qos.logback.classic.Logger testLogger = loggerContext.getLogger(TenderfyTest.class);
        testLogger.setLevel(Level.INFO);
        
        // Set logger của Selenium ở mức OFF (Optional)
        ch.qos.logback.classic.Logger seleniumLogger = loggerContext.getLogger("org.openqa.selenium.remote.http.WebSocket");
        seleniumLogger.setLevel(Level.OFF);
    }

    // Khai báo các biến sử dụng trong test
    // WebDriver: Đối tượng để điều khiển trình duyệt
    // WebDriverWait: Đối tượng để đợi các phần tử trang web được tải
    // BrowserMobProxy: Đối tượng để điều khiển proxy
    private WebDriver driver;
    private WebDriverWait wait;
    private BrowserMobProxy proxy;

    //Bước 1: Setup
    @BeforeEach
    public void setup() {
        // Bật proxy (Giống ProxyMan)
        proxy = new BrowserMobProxyServer();
        proxy.start(0);

        // Cấu hình để phiên Selenium sử dụng proxy vừa tạo ở trên
        Proxy seleniumProxy = ClientUtil.createSeleniumProxy(proxy);

        // Cấu hình Chrome options
        ChromeOptions options = new ChromeOptions();
        options.setProxy(seleniumProxy);
        options.setAcceptInsecureCerts(true);
        options.addArguments("--remote-allow-origins=*");

        // Cấu hình WebDriver với phiên bản ChromeDriver mới nhất
        WebDriverManager.chromedriver().setup();
        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        // Bật capture HAR
        // HAR là gì: HAR (HTTP Archive) là một file lưu trữ dữ liệu giao tiếp giữa trình duyệt và server
        proxy.enableHarCaptureTypes(CaptureType.REQUEST_CONTENT, CaptureType.RESPONSE_CONTENT);
    }

    //Bước 2: Test
    @Test
    public void testTenderfyFlow() {
        // Bắt đầu capture HAR
        // Tên HAR: tenderfy_test
        proxy.newHar("tenderfy_test");

        // Truy cập trang đăng nhập
        driver.get("https://tenderfyfe.vercel.app/sign-in");

        // Nhập username và password
        WebElement usernameField = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//input[@name='username']")));
        WebElement passwordField = driver.findElement(By.xpath("//input[@name='password']"));
        WebElement signInButton = driver.findElement(By.xpath("//button[@type='submit']"));

        usernameField.sendKeys("client@tenderfy.io");
        passwordField.sendKeys("Password.123@");
        signInButton.click();

        // Chờ trang đăng nhập hoàn tất
        wait.until(ExpectedConditions.urlContains("/home"));

        // Truy cập trang chi tiết người dùng
        driver.get("https://tenderfyfe.vercel.app/home/user-details");

        // Chờ trang chi tiết người dùng tải hoàn tất
        wait.until(ExpectedConditions.presenceOfElementLocated(By.name("contact_name")));

        // Lấy dữ liệu HAR
        Har har = proxy.getHar();

        // Tìm phản hồi API trong dữ liệu HAR
        // Tìm API https://tenderfy-10c43c184c30.herokuapp.com/api/company-profile/employee với phương thức GET
        har.getLog().getEntries().stream()
            .filter(entry -> entry.getRequest().getUrl().contains("https://tenderfy-10c43c184c30.herokuapp.com/api/company-profile/employee")
                          && "GET".equalsIgnoreCase(entry.getRequest().getMethod()))
            .findFirst()
            // Nếu tìm thấy, log ra thông tin chi tiết
            .ifPresent(entry -> {
                logger.info("Found matching API entry:");
                logger.info("URL: " + entry.getRequest().getUrl());
                logger.info("Method: " + entry.getRequest().getMethod());
                logger.info("Response Status: " + entry.getResponse().getStatus());
                logger.info("Response Content Type: " + entry.getResponse().getContent().getMimeType());
                logger.info("Response Size: " + entry.getResponse().getContent().getSize());
                
                // Lấy nội dung phản hồi từ API
                String apiResponse = entry.getResponse().getContent().getText();
                logger.info("API Response: " + apiResponse);

                try {
                    // Chuyển đổi nội dung phản hồi API thành JSON => để lấy dữ liệu mong muốn
                    JSONObject jsonResponse = new JSONObject(apiResponse);
                    JSONObject employeeData = jsonResponse.getJSONObject("data").getJSONObject("employee");

                    // Lấy dữ liệu mong muốn từ phản hồi API (Set expected)
                    String expectedContactName = employeeData.getString("contact_name");
                    String expectedRole = employeeData.getString("role");
                    String expectedEmail = employeeData.getString("email");
                    String expectedPhoneNumber = employeeData.getString("phone_number");

                    // Chờ các trường được điền. (Get actual)
                    // đợi cho đến khi trường contact_name được tìm thấy
                    WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
                    wait.until(ExpectedConditions.presenceOfElementLocated(By.name("contact_name")));

                    // Lấy giá trị từ các trường đã được điền (Get actual)
                    String contactName = driver.findElement(By.name("contact_name")).getAttribute("value");
                    String role = driver.findElement(By.name("role")).getAttribute("value");
                    String email = driver.findElement(By.name("email")).getAttribute("value");
                    String phoneNumber = driver.findElement(By.name("phone_number")).getAttribute("value");

                    // So sánh dữ liệu mong muốn và dữ liệu thực tế (Expected và Actual)
                    assertEquals(expectedContactName, contactName, "Contact name mismatch");
                    assertEquals(expectedRole, role, "Role mismatch");
                    assertEquals(expectedEmail, email, "Email mismatch");
                    assertEquals(expectedPhoneNumber, phoneNumber, "Phone number mismatch");

                    logger.info("All assertions passed successfully!");
                } catch (JSONException e) {
                    logger.error("Failed to parse JSON response: " + e.getMessage());
                    logger.error("Raw API response: " + apiResponse);
                    throw e; //
                }
            });

        // Kiểm tra xem API có được gọi không, nếu không thì log lỗi
        if (!har.getLog().getEntries().stream()
                .anyMatch(entry -> entry.getRequest().getUrl().contains("https://tenderfy-10c43c184c30.herokuapp.com/api/company-profile/employee")
                              && "GET".equalsIgnoreCase(entry.getRequest().getMethod()))) {
            logger.error("No matching API request found in HAR data");
        }
    }

    //Bước 3: Cleanup
    @AfterEach
    // Đóng trình duyệt và dừng proxy
    public void tearDown() {
        if (driver != null) {
            driver.quit();
        }
        if (proxy != null) {
            proxy.stop();
        }
    }
}