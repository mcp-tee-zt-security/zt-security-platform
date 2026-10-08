package com.zt.security.compliance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.security.common.TenantSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ComplianceExportService {
    private final ComplianceAssessmentRepository repo;
    private final TenantSession tenant;
    private final ObjectMapper mapper;
    public ComplianceExportService(ComplianceAssessmentRepository repo,
    TenantSession tenant,ObjectMapper mapper){
        this.repo=repo;
        this.tenant=tenant;
        this.mapper=mapper;
        }
    @Transactional(readOnly=true) public Export export(UUID t,UUID id,
    String format){
        tenant.set(t);
        ComplianceAssessment a=repo.findByIdAndTenantId(id,
        t).orElseThrow(()->new SecurityException("compliance assessment not found"));
        String f=format.toLowerCase(Locale.ROOT);
        return switch(f){
            case "csv"->new Export("text/csv",
            "compliance-"+id+".csv",csv(a).getBytes(StandardCharsets.UTF_8));
            case "xlsx"->new Export("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "compliance-"+id+".xlsx",xlsx(a));
            case "pdf"->new Export("application/pdf",
"compliance-"+id+".pdf",pdf(a));
default->throw new IllegalArgumentException("format must be csv, xlsx or pdf");
        }
        ;
        }
    private List<Map<String,Object>> controls(ComplianceAssessment a){
        try{
            return mapper.readValue(a.getControls(),new TypeReference<List<Map<String,
            Object>>>(){
            }
            );
            }
            catch(Exception e){
                return List.of();
            }
            }
    private String csv(ComplianceAssessment a){
        StringBuilder s=new StringBuilder("framework,period_start,period_end,status,score"
+
",evidence_count,control_id," +
"control_name,met,evidence,report_hash\n");
        for(Map<String,Object> c:controls(a))s.append(row(a.getFramework())).append(',').append(row(a.
        getPeriodStart())).append(',').append(row(a.getPeriodEnd())).append(',').append(row(a.getStatus())).append(',').
        append(a.getScore()).append(',').append(a.getEvidenceCount()).append(',').append(row(c.get("id"))).append(',').
        append(row(c.get("name"))).append(',').append(row(c.get("met"))).append(',').append(row(c.get("evidence"))).
        append(',').append(row(a.getReportHash())).append('\n');
        return s.toString();
        }
    private byte[] xlsx(ComplianceAssessment a){
        try{
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            ZipOutputStream z=new ZipOutputStream(out);
            put(z,"[Content_Types].xml",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types " +
"xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
"<Default Extension=\"rels\" " +
"ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
"<Default Extension=\"xml\" ContentType=\"application/xml\"/><Override " +
"PartName=\"/xl/workbook.xml\" " +
"ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.s" +
"heet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" " +
"ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.w" +
"orksheet+xml\"/></Types>");
            put(z,"_rels/.rels","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships " +
"xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
"<Relationship Id=\"rId1\" " +
"Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/" +
"officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            put(z,"xl/_rels/workbook.xml.rels","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships " +
"xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
"<Relationship Id=\"rId1\" " +
"Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/" +
"worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
            put(z,"xl/workbook.xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook " +
"xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
"xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationshi" +
"ps\"><sheets><sheet name=\"Evidence\" sheetId=\"1\" r:id=\"rId1\"/>" +
"</sheets></workbook>");
            StringBuilder sh=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet " +
"xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
"<sheetData>");
            sh.append(rowXml(List.of("Framework","Period Start","Period End","Status",
            "Score","Evidence Count","Control","Description","Met","Evidence","Report Hash")));
            for(Map<String,Object> c:controls(a))sh.append(rowXml(List.of(a.getFramework(),
            a.getPeriodStart(),a.getPeriodEnd(),a.getStatus(),a.getScore(),a.getEvidenceCount(),
            c.get("id"),c.get("name"),c.get("met"),c.get("evidence"),a.getReportHash())));
            sh.append("</sheetData></worksheet>");
            put(z,"xl/worksheets/sheet1.xml",
            sh.toString());
            z.close();
            return out.toByteArray();
            }
            catch(Exception e){
            throw new IllegalStateException("xlsx export failed",e);
            }
            }
    private String rowXml(List<?> vals){
        StringBuilder s=new StringBuilder("<row>");
        for(Object v:vals)s.append("<c t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(xml(v)).
        append("</t></is></c>");
        return s.append("</row>").toString();
        }
    private void put(ZipOutputStream z,String n,String x)throws IOException{
        z.putNextEntry(new ZipEntry(n));
        z.write(x.getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
        }
    private byte[] pdf(ComplianceAssessment a){
        StringBuilder text=new StringBuilder();
        text.append("Zero Trust Compliance Evidence Report\\n\\nFramework: ").append(a.getFramework()).
        append("  Status: ").append(a.getStatus()).append("  Score: ").append(String.format(Locale.ROOT,
        "%.1f",a.getScore())).append("\\n");
        text.append("Period: ").append(a.getPeriodStart()).append(" - ").
        append(a.getPeriodEnd()).append("\\nEvidence count: ").append(a.getEvidenceCount()).append("\\nReport hash: ").
        append(a.getReportHash()).append("\\n\\nControls\\n");
        for(Map<String,Object> c:controls(a))text.append(String.valueOf(c.get("id"))).append(" | ").append(String.
        valueOf(c.get("name"))).append(" | met=").append(String.valueOf(c.get("met"))).append("\\n");
        text.append("\\nInternal evidence report only; not a certification or independent audit opinion.");
        return SimplePdf.build(text.toString());
        }
    private String row(Object x){
        String s=String.valueOf(x==null?"":x);
        return "\""+s.replace("\"","\"\"")+"\"";
        }
        private String xml(Object x){
        String s=String.valueOf(x==null?"":x);
        return s.replace("&","&amp;").replace("<",
        "&lt;").replace(">","&gt;").replace("\"","&quot;");
        }
    public record Export(String contentType,String filename,byte[] bytes){
    }

    static final class SimplePdf {
        static byte[] build(String raw){
            try{
                String[] lines=raw.replace("\\r","").split("\\n");
                StringBuilder stream=new
StringBuilder("BT /F1 10 Tf 50 760 Td 12 TL\\n");
                for(String line:lines){
                    String safe=line.replace("\\","\\\\").replace("(",
                    "\\(").replace(")","\\)");
                    if(safe.length()>110)safe=safe.substring(0,110);
                    stream.append("(").append(safe).append(") Tj T*\\n");
                    }
                    stream.append("ET");
                byte[] content=stream.toString().getBytes(StandardCharsets.ISO_8859_1);
                List<byte[]> objs=new ArrayList<>();
                objs.add("<< /Type /Catalog /Pages 2 0 R >>".getBytes());
                objs.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>".getBytes());
                objs.add(("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792]"
+
" /Resources << /Font " +
"<< /F1 4 0 R >> >> /Contents 5 0 R >>").getBytes(StandardCharsets.ISO_8859_1));
                objs.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>".getBytes());
                objs.add(("<< /Length "+content.length+" >>\\nstream\\n").getBytes(StandardCharsets.ISO_8859_1));
                objs.set(4,concat(objs.get(4),concat(content,"\\nendstream".getBytes(StandardCharsets.ISO_8859_1))));
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                out.write("%PDF-1.4\\n".getBytes(StandardCharsets.
                ISO_8859_1));
                List<Integer> offsets=new ArrayList<>();
                for(int i=0;i<objs.size();i++){
                    offsets.add(out.size());
                    out.write(((i+1)+" 0 obj\\n").getBytes(StandardCharsets.ISO_8859_1));
                    out.write(objs.get(i));
                    out.write("\\nendobj\\n".getBytes(StandardCharsets.ISO_8859_1));
                }
                int xref=out.size();
                out.write(("xref\\n0 "+(objs.size()+1)+"\\n0000000000 65535 f \\n").
                getBytes(StandardCharsets.ISO_8859_1));
            for(int o:offsets)out.write(String.format(Locale.ROOT,"%010d 00000 n \\n",
            o).getBytes(StandardCharsets.ISO_8859_1));
            out.write(("trailer\\n<< /Size "+(objs.size()+
            1)+" /Root 1 0 R >>\\nstartxref\\n"+xref+"\\n%%EOF").getBytes(StandardCharsets.ISO_8859_1));
            return out.toByteArray();
            }
            catch(Exception e){
                throw new IllegalStateException(e);
        }
        }
        static byte[] concat(byte[] a,byte[] b){
            byte[] x=new byte[a.length+b.length];
            System.arraycopy(a,0,x,0,a.length);
            System.arraycopy(b,0,x,a.length,b.length);
            return x;
            }
            }
}
