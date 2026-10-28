import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import za.co.transactiondispute.config.SecurityConfig;
public class Probe {
  public static void main(String[] args) throws Exception {
    SecurityConfig c = new SecurityConfig();
    UserDetailsService uds = c.userDetailsService(new BCryptPasswordEncoder(4));
    System.out.println(uds.loadUserByUsername("customer-user").getAuthorities());
    System.out.println(uds.loadUserByUsername("agent-user").getAuthorities());
  }
}
