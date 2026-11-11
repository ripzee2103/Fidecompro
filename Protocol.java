package fidecompro;
import java.io.*;
import java.math.*;
import java.util.*;

/** Objetos compartidos entre cliente y servidor (Java estándar). */
public final class Protocol {
    private Protocol() {}
    public static class Request implements Serializable {
        private static final long serialVersionUID=1L;
        public String action, token;
        public Object data;
        public Request(String action,String token,Object data){this.action=action;this.token=token;this.data=data;}
    }
    public static class Response implements Serializable {
        private static final long serialVersionUID=1L;
        public boolean ok; public String message; public Object data;
        public Response(boolean ok,String message,Object data){this.ok=ok;this.message=message;this.data=data;}
    }
    public static class Login implements Serializable {
        private static final long serialVersionUID=1L;
        public String username,password;
        public Login(String u,String p){username=u;password=p;}
    }
    public static class User implements Serializable {
        private static final long serialVersionUID=1L;
        public String username,role;
        public User(String u,String r){username=u;role=r;}
        public String toString(){return username+" ("+role+")";}
    }
    public static class Session implements Serializable {
        private static final long serialVersionUID=1L;
        public String token; public User user;
        public Session(String t,User u){token=t;user=u;}
    }
    public static class Customer implements Serializable {
        private static final long serialVersionUID=1L;
        public String id,name,phone,email;
        public Customer(String i,String n,String p,String e){id=i;name=n;phone=p;email=e;}
        public String toString(){return id+" - "+name;}
    }
    public static class Product implements Serializable {
        private static final long serialVersionUID=1L;
        public String code,name,category; public BigDecimal price; public int stock;
        public Product(String c,String n,String cat,BigDecimal p,int s){code=c;name=n;category=cat;price=p;stock=s;}
        public String toString(){return code+" - "+name+" (stock: "+stock+")";}
    }
    /** Herencia: artículos de factura con distintos comportamientos de cálculo. */
    public static abstract class InvoiceLine implements Serializable {
        private static final long serialVersionUID=1L;
        public String code,name; public int quantity; public BigDecimal unitPrice;
        protected InvoiceLine(String c,String n,int q,BigDecimal p){code=c;name=n;quantity=q;unitPrice=p;}
        public abstract BigDecimal subtotal();
    }
    public static class StandardLine extends InvoiceLine {
        private static final long serialVersionUID=1L;
        public StandardLine(String c,String n,int q,BigDecimal p){super(c,n,q,p);}
        @Override public BigDecimal subtotal(){return unitPrice.multiply(BigDecimal.valueOf(quantity));}
    }
    public static class Invoice implements Serializable {
        private static final long serialVersionUID=1L;
        public long number;public Date date;public String customerId,customerName,cashier;
        public List<InvoiceLine> lines=new ArrayList<>();
        public BigDecimal subtotal,tax,total;
        public String toString(){return "Factura #"+number+" - "+customerName+" - ₡"+total;}
    }
    public static class Sale implements Serializable {
        private static final long serialVersionUID=1L;
        public String customerId;public Map<String,Integer> quantities;
        public Sale(String id,Map<String,Integer> q){customerId=id;quantities=q;}
    }
    public static class BusinessException extends RuntimeException {
        public BusinessException(String message){super(message);}
    }
}
