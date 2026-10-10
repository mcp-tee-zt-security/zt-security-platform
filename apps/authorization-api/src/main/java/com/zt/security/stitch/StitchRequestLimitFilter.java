package com.zt.security.stitch;

import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;

@Component @ConditionalOnProperty(name="zt.stitch.enabled",havingValue="true")
public class StitchRequestLimitFilter extends OncePerRequestFilter {
    private static final int LIMIT=4*1024*1024;
    @Override protected boolean shouldNotFilter(HttpServletRequest r){return !(r.getServletPath().startsWith("/v1/integrations/stitch/")||r.getServletPath().startsWith("/v1/integrations/retrieval/"))||!("POST".equals(r.getMethod())||"PUT".equals(r.getMethod()));}
    @Override protected void doFilterInternal(HttpServletRequest r,HttpServletResponse response,FilterChain chain)throws IOException,ServletException{
        if(r.getContentLengthLong()>LIMIT){response.sendError(413,"Connector body exceeds 4 MiB");return;}
        byte[] body=r.getInputStream().readNBytes(LIMIT+1);if(body.length>LIMIT){response.sendError(413,"Connector body exceeds 4 MiB");return;}
        chain.doFilter(new HttpServletRequestWrapper(r){
            @Override public ServletInputStream getInputStream(){var bytes=new ByteArrayInputStream(body);return new ServletInputStream(){public int read(){return bytes.read();}public boolean isFinished(){return bytes.available()==0;}public boolean isReady(){return true;}public void setReadListener(ReadListener listener){throw new UnsupportedOperationException("Synchronous connector requests only");}};}
            @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),java.nio.charset.StandardCharsets.UTF_8));}
        },response);
    }
}
