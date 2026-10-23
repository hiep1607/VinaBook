package vn.fs.automation;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import javax.persistence.*;
import org.springframework.stereotype.Repository;
import vn.fs.entity.*;

/** All projections are bounded. No customer contact data is exposed to the admin report. */
@Repository
public class AutomationDataSource {
    public record Book(Long id,String name,String author,long price,int stock,boolean active) {}
    public record FavoriteRow(Long id,Book book) {}
    public record Watch(Long id,Long productId,String state,boolean seen,Instant availableAt) {}
    public record OrderRow(Long id,int status,Instant placedAt,List<Long> productIds,Set<Long> ratedProducts,boolean returned) {}
    public record CartRow(String fingerprint,List<Book> books) {}
    public record Snapshot(List<FavoriteRow> favorites,List<Watch> watches,List<OrderRow> orders,CartRow cart,List<Book> catalog,List<Book> referencedBooks) {}
    public record DemandRow(Long productId,String name,long waitingReaders,int quantity) {}
    public record StockRow(Long productId,String name,int quantity,int threshold,long waitingReaders) {}
    private final EntityManager em;
    public AutomationDataSource(EntityManager em){this.em=em;}

    public User owner(Long id,boolean lock){return lock?em.find(User.class,id,LockModeType.PESSIMISTIC_WRITE):em.find(User.class,id);}
    public void lockGlobal(){em.find(AutomationState.class,"runner",LockModeType.PESSIMISTIC_WRITE);}
    public List<Long> ownersAfter(long after,int limit){return em.createQuery("select u.userId from User u where u.status=true and u.userId>:after order by u.userId",Long.class).setParameter("after",after).setMaxResults(limit).getResultList();}
    public List<Book> catalog(){return em.createQuery("select p from Product p where p.status=true order by p.productId desc",Product.class).setMaxResults(1000).getResultList().stream().map(this::book).collect(Collectors.toList());}
    public Book book(Product p){
        long price=p.getPrice()==null||!Double.isFinite(p.getPrice())?0:Math.round(p.getPrice()*(100-Math.max(0,Math.min(100,p.getDiscount())))/100);
        return new Book(p.getProductId(),p.getName()==null?"Sách":p.getName(),p.getAuthor()==null?"":p.getAuthor().trim(),Math.max(0,price),Math.max(0,p.getQuantity()),Boolean.TRUE.equals(p.getStatus()));
    }
    public Snapshot snapshot(Long owner,List<Book> catalog){
        var favorites=em.createQuery("select f from Favorite f join fetch f.product where f.user.userId=:owner order by f.favoriteId desc",Favorite.class).setParameter("owner",owner).setMaxResults(500).getResultList().stream().map(f->new FavoriteRow(f.getFavoriteId(),book(f.getProduct()))).collect(Collectors.toList());
        var watches=em.createQuery("select w from RestockWatch w where w.ownerId=:owner and w.state<>'CANCELLED' order by w.id desc",RestockWatch.class).setParameter("owner",owner).setMaxResults(100).getResultList().stream().map(w->new Watch(w.getId(),w.getProductId(),w.getState(),w.isSeen(),w.getAvailableAt())).collect(Collectors.toList());
        var orders=em.createQuery("select o from Order o where o.user.userId=:owner order by o.ordersId desc",Order.class).setParameter("owner",owner).setMaxResults(100).getResultList().stream().map(this::order).collect(Collectors.toList());
        var carts=em.createQuery("select c from Cart c where c.user.userId=:owner order by c.cartId desc",Cart.class).setParameter("owner",owner).setMaxResults(1).getResultList();
        CartRow cart=new CartRow("empty",List.of());
        if(!carts.isEmpty()){
            Cart c=carts.get(0);
            var lines=em.createQuery("select d from CartDetail d join fetch d.product where d.cart.cartId=:cart order by d.product.productId",CartDetail.class).setParameter("cart",c.getCartId()).setMaxResults(500).getResultList();
            String fingerprint=c.getCartId()+":"+c.getSyncRevision()+":"+lines.stream().map(d->d.getProduct().getProductId()+"x"+d.getQuantity()).collect(Collectors.joining(","));
            cart=new CartRow(fingerprint,lines.stream().filter(d->d.getQuantity()>0&&d.getProduct().getQuantity()>=d.getQuantity()&&Boolean.TRUE.equals(d.getProduct().getStatus())).map(d->book(d.getProduct())).collect(Collectors.toList()));
        }
        var referenced=new HashSet<Long>();favorites.forEach(f->referenced.add(f.book().id()));watches.forEach(w->referenced.add(w.productId()));orders.forEach(o->referenced.addAll(o.productIds()));cart.books().forEach(b->referenced.add(b.id()));
        var lookup=new ArrayList<Book>();var ids=new ArrayList<>(referenced);
        for(int from=0;from<ids.size();from+=200)lookup.addAll(em.createQuery("select p from Product p where p.productId in :ids",Product.class).setParameter("ids",ids.subList(from,Math.min(from+200,ids.size()))).getResultList().stream().map(this::book).collect(Collectors.toList()));
        return new Snapshot(favorites,watches,orders,cart,catalog,lookup);
    }
    public Order findOrder(Long id){return em.find(Order.class,id);}
    public OrderRow order(Order order){
        var ids=em.createQuery("select d.product.productId from OrderDetail d where d.order.ordersId=:order order by d.orderDetailId",Long.class).setParameter("order",order.getOrdersId()).setMaxResults(500).getResultList();
        var rated=new HashSet<>(em.createQuery("select r.product.productId from Rate r where r.user.userId=:owner and r.product.productId in :ids",Long.class).setParameter("owner",order.getUser().getUserId()).setParameter("ids",ids.isEmpty()?List.of(-1L):ids).setMaxResults(500).getResultList());
        long returns=em.createQuery("select count(s) from SupportRequest s where s.ownerId=:owner and s.orderId=:order and s.kind='RETURN' and s.status<>'REJECTED'",Long.class).setParameter("owner",order.getUser().getUserId()).setParameter("order",order.getOrdersId()).getSingleResult();
        return new OrderRow(order.getOrdersId(),order.getStatus(),order.getOrderDate()==null?Instant.EPOCH:order.getOrderDate().toInstant(),ids,rated,returns>0);
    }
    public void syncRestockRead(Long owner,AutomationNotice notice){
        if(!"RESTOCK".equals(notice.getType())||notice.getProductId()==null||notice.getSourceReference()==null)return;
        var rows=em.createQuery("select w from RestockWatch w where w.ownerId=:owner and w.productId=:product",RestockWatch.class).setParameter("owner",owner).setParameter("product",notice.getProductId()).setMaxResults(1).getResultList();
        rows.stream().filter(w->w.getAvailableAt()!=null&&w.getAvailableAt().toString().equals(notice.getSourceReference())).forEach(w->w.setSeen(true));
    }
    public void syncAllRestockRead(Long owner){em.createQuery("update RestockWatch w set w.seen=true where w.ownerId=:owner and w.state='AVAILABLE'").setParameter("owner",owner).executeUpdate();}
    public List<DemandRow> demand(){
        var result=new ArrayList<DemandRow>();
        var counts=em.createQuery("select w.productId,count(w) from RestockWatch w where w.state='WAITING' group by w.productId order by count(w) desc,w.productId",Object[].class).setMaxResults(100).getResultList();
        for(var c:counts){Product p=em.find(Product.class,(Long)c[0]);if(p!=null&&Boolean.TRUE.equals(p.getStatus()))result.add(new DemandRow(p.getProductId(),p.getName(),(Long)c[1],p.getQuantity()));}
        return result;
    }
    public long waitingReaders(){return em.createQuery("select count(w) from RestockWatch w,Product p where w.state='WAITING' and w.productId=p.productId and p.status=true",Long.class).getSingleResult();}
    public long lowStockCount(int threshold){return em.createQuery("select count(p) from Product p where p.status=true and p.quantity<=:threshold",Long.class).setParameter("threshold",threshold).getSingleResult();}
    public List<StockRow> lowStock(int threshold){
        var waiting=demand().stream().collect(Collectors.toMap(DemandRow::productId,DemandRow::waitingReaders));
        return em.createQuery("select p from Product p where p.status=true and p.quantity<=:threshold order by p.quantity,p.productId",Product.class).setParameter("threshold",threshold).setMaxResults(100).getResultList().stream().map(p->new StockRow(p.getProductId(),p.getName(),p.getQuantity(),threshold,waiting.getOrDefault(p.getProductId(),0L))).collect(Collectors.toList());
    }
    public long failedDeliveries(){return em.createQuery("select count(n) from AutomationNotice n, MailOutbox m where n.mailJobId=m.id and m.state='FAILED'",Long.class).getSingleResult();}
}
