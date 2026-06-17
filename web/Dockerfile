FROM ubuntu:22.04
ENV DEBIAN_FRONTEND=noninteractive

# 1. Install foundational packages: JDK 21, Nginx, OpenSSH Server, Git, and supervisor
RUN apt-get update && apt-get install -y \
    openjdk-25-jdk \
    nginx \
    openssh-server \
    git \
    supervisor \
    curl \
    && rm -rf /var/lib/apt/lists/*

# 2. Configure SSH Access Server on Port 2222
RUN mkdir /var/run/sshd
RUN echo 'root:Hello@123' | chpasswd
RUN sed -i 's/#PermitRootLogin prohibit-password/PermitRootLogin yes/' /etc/ssh/sshd_config
RUN sed -i 's/Port 22/Port 2222/' /etc/ssh/sshd_config

# 3. Configure Nginx Reverse Proxy Rules
RUN rm /etc/nginx/sites-enabled/default
RUN echo 'server { \
    listen 8443; \
    server_name localhost; \
    location / { \
        proxy_pass http://127.0.0.1:8081; \
        proxy_set_header Host $host; \
        proxy_set_header X-Real-IP $remote_addr; \
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded-for; \
        proxy_set_header X-Forwarded-Proto $scheme; \
    } \
}' > /etc/nginx/sites-available/idcard-proxy

RUN ln -s /etc/nginx/sites-available/idcard-proxy /etc/nginx/sites-enabled/

# 4. Clone and Build your Year 4 DevOps Spring Boot ID Card System Repository
WORKDIR /app
RUN git clone https://github.com/Xieryy/DevOp-Final.git .
RUN chmod +x ./mvnw
RUN ./mvnw clean package -DskipTests

# 5. Create a Supervisor Automation configuration script file 
RUN echo '[supervisord]\nnodaemon=true\n\n\
[program:sshd]\ncommand=/usr/sbin/sshd -D\n\n\
[program:nginx]\ncommand=nginx -g "daemon off;"\n\n\
[program:springboot]\n\
command=java -jar /app/target/idcard-0.0.1-SNAPSHOT.jar --spring.datasource.url=jdbc:mysql://mysql-db:3306/1-Im_Chheangngim-db?createDatabaseIfNotExist=true\n\
directory=/app\n\
autorestart=true\n' > /etc/supervisor/conf.d/supervisord.conf

EXPOSE 8443 2222

CMD ["/usr/bin/supervisord", "-c", "/etc/supervisor/conf.d/supervisord.conf"]