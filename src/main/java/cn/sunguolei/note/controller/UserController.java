package cn.sunguolei.note.controller;

import cn.sunguolei.note.domain.BaseModel;
import cn.sunguolei.note.domain.User;
import cn.sunguolei.note.service.EmailService;
import cn.sunguolei.note.service.UserService;
import cn.sunguolei.note.utils.DesUtil;
import com.sun.xml.internal.messaging.saaj.packaging.mime.MessagingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Controller
@RequestMapping("/user")
public class UserController {

    @Value("${yingnote.key}")
    private String KEY_;
    private UserService userService;
    private EmailService emailService;

    public UserController(UserService userService, EmailService emailService) {
        this.userService = userService;
        this.emailService = emailService;
    }

    @GetMapping("/findUserByUsername")
    public User findUserByUsername(String username) {
        return userService.findUserByUsername(username);
    }

    @GetMapping("/index")
    public String index(Model model) throws Exception {
        //查找用户列表
        List<User> userList = userService.index();
        // 将用户列表存放到 model 中，返回给前端页面
        model.addAttribute("userList", userList);

        return "user/index";
    }

    @GetMapping("/add")
    public String register() {
        return "user/add";
    }

    @PostMapping("/create")
    public String create(User user, Model model) {
        LocalDateTime createTime = LocalDateTime.now();
        user.setCreateTime(createTime);
        String password = new BCryptPasswordEncoder(11).encode(user.getPassword());
        user.setPassword(password);
        User resultCheckUsername = userService.findUserByUsername(user.getUsername());
        User resultCheckEmail = userService.checkEmail(user.getEmail());
        int number = (int) (Math.random() * 90000 + 10000);
        char c = (char) (int) (Math.random() * 26 + 97);
        String code = String.valueOf(number) + c;
        user.setActivateCode(code);
        if (resultCheckUsername != null) {

            model.addAttribute("msg", "注册失败,用户名重复");
            return "user/add";
        } else if (resultCheckEmail != null) {
            model.addAttribute("msg", "注册失败,邮箱重复");
            return "user/add";
        } else {
            userService.create(user);
            try {
                emailService.sendSimpleRegisterMail(user.getEmail(), Locale.CHINA, user);
            } catch (MessagingException e) {
                e.printStackTrace();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
            model.addAttribute("msg", "注册成功");
        }
        return "login";
    }

    @RequestMapping("/activeUser")
    public String checkRegisterCode(Model model, String sign) throws Exception {
        BaseModel baseModel = new BaseModel();

        if (sign == null || sign.length() < 1) {
            baseModel.setReturnCode(101);
            baseModel.setMessage("参数sign非法");
        } else {
            String decode = DesUtil.decrypt(sign, KEY_);

            String[] userArray = decode.split("_");

            if (userArray.length == 2) {
                //解析出userid和code
                String username = userArray[0];
                String code = userArray[1];

                //根据userid和code，判断是否合法注册用户
                User userParam = new User();

                userParam.setUsername(username);
                userParam.setActivateStatus(0);
                userParam.setActivateCode(code);
                int count = userService.getUserCountByIdActivateStatus(userParam);

                if (count > 0) {
                    //如果是合法用户，修改校验标识
                    userParam.setActivateStatus(1);
                    int success = userService.SetUserActivateStatus(userParam);
                    if (success <= 0) {
                        //如果是合法用户，修改校验标识
                        baseModel.setReturnCode(104);
                        baseModel.setMessage("用户不存在或者已注册成功");
                    }
                } else {
                    //如果是合法用户，修改校验标识
                    baseModel.setReturnCode(102);
                    baseModel.setMessage("用户不存在或者已注册成功");
                }
            } else {
                baseModel.setReturnCode(103);
                baseModel.setMessage("加密参数格式错误");
            }
        }

        if (baseModel.getReturnCode() > 0) {
            model.addAttribute("msg", baseModel.getMessage());
            return "user/add";
        } else {
            model.addAttribute("msg", "注册成功，请登录");
            return "login";
        }
    }

    //删除cookie
    @RequestMapping("/toLogout")
    public String toLogout(HttpServletRequest request, HttpServletResponse response) {
        Cookie[] cookies = request.getCookies();
        System.out.println(cookies.length);
        for (Cookie cookie : cookies) {
            //如果找到同名cookie，就将value设置为null，将存活时间设置为0，再替换掉原cookie，这样就相当于删除了。
            if (cookie.getName().equals("token")) {
                cookie.setValue(null);
                cookie.setMaxAge(0);
                cookie.setPath("/");
                response.addCookie(cookie);
                break;
            }
        }
        return "index";
    }
}