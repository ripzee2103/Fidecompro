package fidecompro;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.math.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import static fidecompro.Protocol.*;

/** Servidor TCP concurrente: validaciones y descuentos atómicos del lado servidor. */
public class Server {
    private final Store store;
    private final Map<String,User> sessions=new ConcurrentHashMap<>();
    private final ExecutorService pool=Executors.newFixedThreadPool(12);
    private Server(Path file) throws IOException,ClassNotFoundException {store=Store.load(file);}
    public static void main(String[] args) throws Exception {
        int port=args.length>0?Integer.parseInt(args[0]):5050;
        Server server=new Server(Paths.get("fidecompro-data.ser"));
        try(ServerSocket listener=new ServerSocket(port)) {
            System.out.println("Fidecompro servidor en puerto "+port);
            while(true){Socket socket=listener.accept();server.pool.execute(()->server.serve(socket));}
        }
    }
    private void serve(Socket socket){
        try(Socket s=socket;ObjectOutputStream out=new ObjectOutputStream(s.getOutputStream());ObjectInputStream in=new ObjectInputStream(s.getInputStream())){
            s.setSoTimeout(15000);
            Object obj=in.readObject();
            Response response=obj instanceof Request?handle((Request)obj):fail("Solicitud inválida");
            out.writeObject(response);out.flush();
        }catch(Exception e){System.err.println("Conexión: "+e.getMessage());}
    }
    private Response handle(Request r){
        try {
            if("LOGIN".equals(r.action)){
                Login l=(Login)r.data;
                User u=store.authenticate(l.username,l.password);
                String token=UUID.randomUUID().toString();sessions.put(token,u);
                return ok(new Session(token,u));
            }
            User user=sessions.get(r.token);
            if(user==null)throw new BusinessException("Sesión inválida. Inicie sesión.");
            if("LOGOUT".equals(r.action)){sessions.remove(r.token);return ok(null);}
            switch(r.action){
                case "CUSTOMERS":return ok(store.customers());
                case "PRODUCTS":return ok(store.products());
                case "INVOICES":return ok(store.invoices());
                case "ADD_CUSTOMER":requireAdmin(user);store.addCustomer((Customer)r.data);return ok(null);
                case "ADD_PRODUCT":requireAdmin(user);store.addProduct((Product)r.data);return ok(null);
                case "UPDATE_STOCK":requireAdmin(user);store.adjustStock((String)((Object[])r.data)[0],(Integer)((Object[])r.data)[1]);return ok(null);
                case "SALE":return ok(store.sale((Sale)r.data,user.username));
                default:throw new BusinessException("Operación desconocida");
            }
        }catch(BusinessException|ClassCastException|NullPointerException e){return fail(e.getMessage()==null?"Datos inválidos":e.getMessage());}
        catch(Exception e){e.printStackTrace();return fail("Error interno del servidor");}
    }
    private static void requireAdmin(User u){if(!"ADMIN".equals(u.role))throw new BusinessException("Operación exclusiva de administrador");}
    private static Response ok(Object data){return new Response(true,"Operación exitosa",data);}
    private static Response fail(String msg){return new Response(false,msg,null);}
    private static class Store implements Serializable {
        private static final long serialVersionUID=1L;
        private Map<String,Customer> customers=new LinkedHashMap<>();
        private Map<String,Product> products=new LinkedHashMap<>();
        private List<Invoice> invoices=new ArrayList<>();
        private Map<String,String> passwordHashes=new HashMap<>();
        private Map<String,String> roles=new HashMap<>();
        private long nextNumber=1;
        private transient Path file;
        static Store load(Path path)throws IOException,ClassNotFoundException{
            Store s;
            if(Files.exists(path)){
                try(ObjectInputStream in=new ObjectInputStream(Files.newInputStream(path))){s=(Store)in.readObject();}
            }else{
                s=new Store();s.passwordHashes.put("admin",hash("Admin123!"));s.roles.put("admin","ADMIN");
                s.passwordHashes.put("cajero",hash("Caja123!"));s.roles.put("cajero","CAJERO");
            }
            s.file=path;return s;
        }
        private static String hash(String text){
            try{byte[] digest=MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder b=new StringBuilder();for(byte v:digest)b.append(String.format("%02x",v&255));return b.toString();
            }catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
        }
        synchronized User authenticate(String u,String p){
            if(u==null||p==null||!hash(p).equals(passwordHashes.get(u)))throw new BusinessException("Usuario o contraseña incorrectos");
            return new User(u,roles.get(u));
        }
        synchronized List<Customer> customers(){return new ArrayList<>(customers.values());}
        synchronized List<Product> products(){return new ArrayList<>(products.values());}
        synchronized List<Invoice> invoices(){return new ArrayList<>(invoices);}
        synchronized void addCustomer(Customer c)throws IOException{
            if(c.id==null||c.id.trim().isEmpty()||c.name==null||c.name.trim().isEmpty())throw new BusinessException("Identificación y nombre obligatorios");
            if(customers.containsKey(c.id))throw new BusinessException("Identificación duplicada");
            customers.put(c.id,c);save();
        }
        synchronized void addProduct(Product p)throws IOException{
            if(p.code==null||p.code.trim().isEmpty()||p.name==null||p.name.trim().isEmpty()||p.category==null||p.category.trim().isEmpty())throw new BusinessException("Código, nombre y categoría obligatorios");
            if(p.price==null||p.price.signum()<0||p.stock<0)throw new BusinessException("Precio o stock inválido");
            if(products.containsKey(p.code))throw new BusinessException("Código duplicado");
            products.put(p.code,p);save();
        }
        synchronized void adjustStock(String code,int delta)throws IOException{
            Product p=products.get(code);if(p==null)throw new BusinessException("Producto inexistente");
            if((long)p.stock+delta<0||(long)p.stock+delta>Integer.MAX_VALUE)throw new BusinessException("Stock inválido");
            p.stock+=delta;save();
        }
        synchronized Invoice sale(Sale s,String cashier)throws IOException{
            if(s==null||!customers.containsKey(s.customerId))throw new BusinessException("Seleccione un cliente válido");
            if(s.quantities==null||s.quantities.isEmpty())throw new BusinessException("La factura debe contener productos");
            // Primero valida TODOS los artículos. No descuenta existencias en ventas fallidas.
            for(Map.Entry<String,Integer> entry:s.quantities.entrySet()){
                Product p=products.get(entry.getKey());Integer q=entry.getValue();
                if(p==null||q==null||q<=0)throw new BusinessException("Producto o cantidad inválidos");
                if(p.stock<q)throw new BusinessException("Stock insuficiente para "+p.name);
            }
            Invoice f=new Invoice();f.number=nextNumber;f.date=new Date();f.customerId=s.customerId;
            f.customerName=customers.get(s.customerId).name;f.cashier=cashier;f.subtotal=BigDecimal.ZERO;
            for(Map.Entry<String,Integer> e:s.quantities.entrySet()){
                Product p=products.get(e.getKey());
                InvoiceLine line=new StandardLine(p.code,p.name,e.getValue(),p.price);
                f.lines.add(line);f.subtotal=f.subtotal.add(line.subtotal()); // polimorfismo
            }
            f.subtotal=f.subtotal.setScale(2,RoundingMode.HALF_UP);
            f.tax=f.subtotal.multiply(new BigDecimal("0.13")).setScale(2,RoundingMode.HALF_UP);
            f.total=f.subtotal.add(f.tax);
            for(Map.Entry<String,Integer> e:s.quantities.entrySet())products.get(e.getKey()).stock-=e.getValue();
            invoices.add(f);nextNumber++;save();return f;
        }
        private void save()throws IOException{
            Path tmp=Paths.get(file.toString()+".tmp");
            try(ObjectOutputStream out=new ObjectOutputStream(Files.newOutputStream(tmp))){out.writeObject(this);}
            try{Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}
        }
    }
}
