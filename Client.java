package fidecompro;
import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.math.*;
import java.text.*;
import java.util.*;
import java.util.List;
import static fidecompro.Protocol.*;

/** Cliente gráfico Swing para Fidecompro. */
public class Client extends JFrame {
    private final String host;private final int port;
    private Session session;
    private final JPanel root=new JPanel(new BorderLayout());
    private final DefaultTableModel customerModel=model("Identificación","Nombre","Teléfono","Correo");
    private final DefaultTableModel productModel=model("Código","Nombre","Categoría","Precio (₡)","Stock");
    private final DefaultTableModel invoiceModel=model("Número","Fecha","Cliente","Total (₡)");
    private JComboBox<Customer> customerBox;
    private JComboBox<Product> productBox;
    private final DefaultTableModel cartModel=model("Código","Producto","Cantidad","Precio (₡)","Subtotal (₡)");
    private final Map<String,Integer> cart=new LinkedHashMap<>();
    private List<Invoice> invoiceCache=new ArrayList<>();
    private List<Customer> customerCache=new ArrayList<>();
    private List<Product> productCache=new ArrayList<>();
    public Client(String host,int port){super("Fidecompro · Facturación e inventario");this.host=host;this.port=port;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);setSize(1000,650);setMinimumSize(new Dimension(760,520));
        setLocationRelativeTo(null);setContentPane(root);loginScreen();}
    public static void main(String[] args){String h=args.length>0?args[0]:"127.0.0.1";
        int p=args.length>1?Integer.parseInt(args[1]):5050;
        SwingUtilities.invokeLater(()->new Client(h,p).setVisible(true));}
    private static DefaultTableModel model(String... columns){return new DefaultTableModel(columns,0){
        @Override public boolean isCellEditable(int row,int col){return false;}};}
    private Response call(String action,Object data){
        try(Socket socket=new Socket()){
            socket.connect(new InetSocketAddress(host,port),3000);socket.setSoTimeout(10000);
            try(ObjectOutputStream out=new ObjectOutputStream(socket.getOutputStream());ObjectInputStream in=new ObjectInputStream(socket.getInputStream())){
                out.writeObject(new Request(action,session==null?null:session.token,data));out.flush();
                Response r=(Response)in.readObject();if(!r.ok)throw new BusinessException(r.message);return r;
            }
        }catch(BusinessException e){throw e;}
        catch(Exception e){throw new BusinessException("No se pudo comunicar con el servidor: "+e.getMessage());}
    }
    private void action(Runnable r){try{r.run();}catch(Exception e){JOptionPane.showMessageDialog(this,e.getMessage(),"Aviso",JOptionPane.ERROR_MESSAGE);}}
    private void loginScreen(){root.removeAll();JPanel panel=new JPanel(new GridBagLayout());
        JPanel form=new JPanel(new GridLayout(0,1,7,7));form.setBorder(BorderFactory.createTitledBorder("Ingreso al sistema"));
        JTextField user=new JTextField("admin",20);JPasswordField pass=new JPasswordField(20);
        JButton enter=new JButton("Iniciar sesión");form.add(new JLabel("Usuario"));form.add(user);
        form.add(new JLabel("Contraseña"));form.add(pass);form.add(enter);
        form.add(new JLabel("Usuarios de prueba: admin / cajero"));panel.add(form);root.add(panel);
        enter.addActionListener(e->action(()->{session=(Session)call("LOGIN",new Login(user.getText().trim(),new String(pass.getPassword()))).data;dashboard();}));
        pass.addActionListener(e->enter.doClick());root.revalidate();root.repaint();}
    private void dashboard(){root.removeAll();JPanel top=new JPanel(new BorderLayout());
        top.add(new JLabel("  FIDECOMPRO  |  "+session.user),BorderLayout.WEST);
        JButton logout=new JButton("Cerrar sesión");logout.addActionListener(e->action(()->{call("LOGOUT",null);session=null;loginScreen();}));
        top.add(logout,BorderLayout.EAST);root.add(top,BorderLayout.NORTH);
        JTabbedPane tabs=new JTabbedPane();tabs.addTab("Clientes",customersPanel());tabs.addTab("Inventario",productsPanel());
        tabs.addTab("Nueva factura",salesPanel());tabs.addTab("Consultar facturas",invoicesPanel());root.add(tabs);
        refreshAll();root.revalidate();root.repaint();}
    private JPanel customersPanel(){JPanel p=new JPanel(new BorderLayout(8,8));
        p.add(new JScrollPane(new JTable(customerModel)),BorderLayout.CENTER);
        JPanel controls=new JPanel(new FlowLayout(FlowLayout.LEFT));JButton add=new JButton("Registrar cliente"),reload=new JButton("Actualizar lista");
        controls.add(add);controls.add(reload);p.add(controls,BorderLayout.NORTH);
        add.setEnabled("ADMIN".equals(session.user.role));
        add.addActionListener(e->action(()->{JTextField id=new JTextField(),name=new JTextField(),phone=new JTextField(),email=new JTextField();
            if(dialog("Nuevo cliente",new String[]{"Identificación","Nombre","Teléfono","Correo"},new JTextField[]{id,name,phone,email})){
                call("ADD_CUSTOMER",new Customer(id.getText().trim(),name.getText().trim(),phone.getText().trim(),email.getText().trim()));refreshAll();}}));
        reload.addActionListener(e->action(this::refreshAll));return p;}
    private JPanel productsPanel(){JPanel p=new JPanel(new BorderLayout(8,8));p.add(new JScrollPane(new JTable(productModel)),BorderLayout.CENTER);
        JPanel controls=new JPanel(new FlowLayout(FlowLayout.LEFT));JButton add=new JButton("Registrar producto"),stock=new JButton("Ajustar existencias"),reload=new JButton("Actualizar lista");
        controls.add(add);controls.add(stock);controls.add(reload);p.add(controls,BorderLayout.NORTH);
        add.setEnabled("ADMIN".equals(session.user.role));stock.setEnabled("ADMIN".equals(session.user.role));
        add.addActionListener(e->action(()->{JTextField code=new JTextField(),name=new JTextField(),cat=new JTextField(),price=new JTextField(),qty=new JTextField();
            if(dialog("Nuevo producto",new String[]{"Código","Nombre","Categoría","Precio (₡)","Stock"},new JTextField[]{code,name,cat,price,qty})){
                call("ADD_PRODUCT",new Product(code.getText().trim(),name.getText().trim(),cat.getText().trim(),new BigDecimal(price.getText().trim()),Integer.parseInt(qty.getText().trim())));refreshAll();}}));
        stock.addActionListener(e->action(()->{String code=JOptionPane.showInputDialog(this,"Código del producto:");if(code==null)return;
            String delta=JOptionPane.showInputDialog(this,"Cambio de stock (ejemplo: 10 o -2):");if(delta==null)return;
            call("UPDATE_STOCK",new Object[]{code.trim(),Integer.parseInt(delta.trim())});refreshAll();}));
        reload.addActionListener(e->action(this::refreshAll));return p;}
    private JPanel salesPanel(){JPanel p=new JPanel(new BorderLayout(8,8));JPanel top=new JPanel(new FlowLayout(FlowLayout.LEFT));
        customerBox=new JComboBox<>();productBox=new JComboBox<>();JSpinner qty=new JSpinner(new SpinnerNumberModel(1,1,100000,1));
        JButton add=new JButton("Agregar al carrito"),reload=new JButton("Actualizar catálogo");
        top.add(new JLabel("Cliente:"));top.add(customerBox);top.add(new JLabel("Producto:"));top.add(productBox);
        top.add(new JLabel("Cantidad:"));top.add(qty);top.add(add);top.add(reload);p.add(top,BorderLayout.NORTH);
        p.add(new JScrollPane(new JTable(cartModel)),BorderLayout.CENTER);
        JPanel bottom=new JPanel(new FlowLayout(FlowLayout.RIGHT));JButton clear=new JButton("Vaciar carrito"),confirm=new JButton("Confirmar factura");
        bottom.add(clear);bottom.add(confirm);p.add(bottom,BorderLayout.SOUTH);
        add.addActionListener(e->action(()->{Product product=(Product)productBox.getSelectedItem();if(product==null)throw new BusinessException("Seleccione un producto");
            int count=(Integer)qty.getValue();int total=cart.getOrDefault(product.code,0)+count;
            if(total>product.stock)throw new BusinessException("No hay suficientes unidades");cart.put(product.code,total);refreshCart();}));
        clear.addActionListener(e->{cart.clear();refreshCart();});
        reload.addActionListener(e->action(this::refreshAll));
        confirm.addActionListener(e->action(()->{Customer customer=(Customer)customerBox.getSelectedItem();
            if(customer==null||cart.isEmpty())throw new BusinessException("Seleccione cliente y productos");
            Invoice invoice=(Invoice)call("SALE",new Sale(customer.id,new LinkedHashMap<>(cart))).data;
            cart.clear();refreshAll();refreshCart();JOptionPane.showMessageDialog(this,"Factura #"+invoice.number+" generada. Total ₡"+invoice.total);
            if(JOptionPane.showConfirmDialog(this,"¿Exportar comprobante TXT?","Factura",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION)export(invoice);}));
        return p;}
    private JPanel invoicesPanel(){JPanel p=new JPanel(new BorderLayout(8,8));JTable table=new JTable(invoiceModel);
        p.add(new JScrollPane(table),BorderLayout.CENTER);JPanel controls=new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton refresh=new JButton("Actualizar"),detail=new JButton("Ver detalle"),export=new JButton("Exportar TXT");
        controls.add(refresh);controls.add(detail);controls.add(export);p.add(controls,BorderLayout.NORTH);
        refresh.addActionListener(e->action(this::refreshAll));
        detail.addActionListener(e->action(()->{Invoice i=selectedInvoice(table);JTextArea area=new JTextArea(receipt(i),22,65);area.setEditable(false);
            JOptionPane.showMessageDialog(this,new JScrollPane(area),"Factura #"+i.number,JOptionPane.INFORMATION_MESSAGE);}));
        export.addActionListener(e->action(()->export(selectedInvoice(table))));return p;}
    private Invoice selectedInvoice(JTable t){int row=t.getSelectedRow();if(row<0)throw new BusinessException("Seleccione una factura");
        return invoiceCache.get(t.convertRowIndexToModel(row));}
    @SuppressWarnings("unchecked") private void refreshAll(){
        customerCache=(List<Customer>)call("CUSTOMERS",null).data;productCache=(List<Product>)call("PRODUCTS",null).data;
        invoiceCache=(List<Invoice>)call("INVOICES",null).data;
        customerModel.setRowCount(0);for(Customer c:customerCache)customerModel.addRow(new Object[]{c.id,c.name,c.phone,c.email});
        productModel.setRowCount(0);for(Product p:productCache)productModel.addRow(new Object[]{p.code,p.name,p.category,p.price,p.stock});
        invoiceModel.setRowCount(0);for(Invoice i:invoiceCache)invoiceModel.addRow(new Object[]{i.number,new SimpleDateFormat("dd/MM/yyyy HH:mm").format(i.date),i.customerName,i.total});
        if(customerBox!=null){Object previous=customerBox.getSelectedItem();String id=previous instanceof Customer?((Customer)previous).id:null;
            customerBox.removeAllItems();for(Customer c:customerCache)customerBox.addItem(c);
            for(int j=0;j<customerBox.getItemCount();j++)if(customerBox.getItemAt(j).id.equals(id))customerBox.setSelectedIndex(j);}
        if(productBox!=null){productBox.removeAllItems();for(Product p:productCache)productBox.addItem(p);}
        refreshCart();
    }
    private void refreshCart(){cartModel.setRowCount(0);for(Map.Entry<String,Integer> e:cart.entrySet()){
        for(Product p:productCache)if(p.code.equals(e.getKey())){
            cartModel.addRow(new Object[]{p.code,p.name,e.getValue(),p.price,p.price.multiply(BigDecimal.valueOf(e.getValue()))});break;}}}
    private boolean dialog(String title,String[] labels,JTextField[] fields){JPanel panel=new JPanel(new GridLayout(0,2,7,7));
        for(int i=0;i<labels.length;i++){panel.add(new JLabel(labels[i]));panel.add(fields[i]);}
        return JOptionPane.showConfirmDialog(this,panel,title,JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION;}
    private static String receipt(Invoice i){StringBuilder b=new StringBuilder("FIDECOMPRO - COMPROBANTE DE VENTA\n");
        b.append("Factura: ").append(i.number).append("\nFecha: ").append(new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(i.date));
        b.append("\nCliente: ").append(i.customerName).append(" (").append(i.customerId).append(")\nCajero: ").append(i.cashier).append("\n\n");
        for(InvoiceLine line:i.lines)b.append(line.code).append(" | ").append(line.name).append(" | ").append(line.quantity)
            .append(" x ₡").append(line.unitPrice).append(" = ₡").append(line.subtotal()).append("\n");
        return b.append("\nSubtotal: ₡").append(i.subtotal).append("\nIVA (13%): ₡").append(i.tax)
            .append("\nTOTAL: ₡").append(i.total).append("\n").toString();}
    private void export(Invoice invoice){JFileChooser chooser=new JFileChooser();chooser.setSelectedFile(new File("Factura_"+invoice.number+".txt"));
        if(chooser.showSaveDialog(this)==JFileChooser.APPROVE_OPTION){try{
            Files.write(chooser.getSelectedFile().toPath(),receipt(invoice).getBytes(StandardCharsets.UTF_8));
            JOptionPane.showMessageDialog(this,"Comprobante guardado correctamente");
        }catch(IOException e){throw new BusinessException("No se pudo guardar: "+e.getMessage());}}}
}
