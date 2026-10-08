package vn.fs.service;
import java.util.*;
import javax.persistence.*;
import javax.validation.Valid;
import javax.validation.constraints.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.fs.config.CurrentUserAccess;
import vn.fs.entity.*;
import vn.fs.repository.*;
@Service
public class CustomerPreferencesService {
    private final CurrentUserAccess access; private final CustomerPreferencesRepository preferences;
    private final FavoriteRepository favorites; private final ProductRepository products; private final EntityManager em;
    private final ObjectMapper json=new ObjectMapper();
    public CustomerPreferencesService(CurrentUserAccess access,CustomerPreferencesRepository preferences,FavoriteRepository favorites,ProductRepository products,EntityManager em){this.access=access;this.preferences=preferences;this.favorites=favorites;this.products=products;this.em=em;}
    private ResponseStatusException fail(HttpStatus status,String message){return new ResponseStatusException(status,message);}
    private User owner(boolean lock){Long id=access.currentUserId();if(id==null)throw fail(HttpStatus.UNAUTHORIZED,"Bạn đăng nhập để đồng bộ đúng tài khoản nhé.");User user=lock?em.find(User.class,id,LockModeType.PESSIMISTIC_WRITE):em.find(User.class,id);if(user==null)throw fail(HttpStatus.UNAUTHORIZED,"Tài khoản không còn khả dụng.");return user;}
    private CustomerPreferences preference(User user){return preferences.findById(user.getUserId()).orElseGet(()->{var p=new CustomerPreferences();p.setOwnerId(user.getUserId());return p;});}
    private <T> T read(String text,TypeReference<T> type){try{return json.readValue(text,type);}catch(Exception e){throw fail(HttpStatus.INTERNAL_SERVER_ERROR,"Chưa đọc được dữ liệu đồng bộ. Vui lòng liên hệ cửa hàng.");}}
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private List<Address> addresses(CustomerPreferences p){return read(p.getAddressesJson(),new TypeReference<List<Address>>(){});}
    private Map<String,Object> dto(User user,CustomerPreferences p){return Map.of("addresses",addresses(p),"avatar",p.getAvatar(),"revision",p.getRevision(),"wishlistProductIds",favorites.findByUser(user).stream().filter(f->f.getProduct()!=null).map(f->f.getProduct().getProductId()).distinct().collect(java.util.stream.Collectors.toList()));}
    @Transactional(readOnly=true) public Map<String,Object> get(){var user=owner(false);return dto(user,preference(user));}
    private void revision(CustomerPreferences p,long revision){if(p.getRevision()!=revision)throw fail(HttpStatus.CONFLICT,"Dữ liệu đã đổi ở thiết bị khác. Bạn tải lại, xem thay đổi rồi lưu lại nhé.");}
    public static void validateAddresses(List<Address> rows){
        if(rows==null || rows.size()>20)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Bạn có thể lưu tối đa 20 địa chỉ.");
        Set<Long> ids=new HashSet<>();int defaults=0;
        for(var a:rows){if(a==null || a.id==null || a.id<1 || a.id>9007199254740991L || !ids.add(a.id)
            || a.name==null || a.name.trim().isBlank() || a.name.length()>80 || a.label==null || a.label.length()>80
            || a.street==null || a.street.trim().length()<5 || a.street.length()>255 || a.phone==null || !a.phone.matches("[+]?[0-9][0-9 .-]{6,18}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tên, điện thoại hoặc địa chỉ chưa hợp lệ.");
            if(a.defaultAddress)defaults++;
        }
        if(!rows.isEmpty() && defaults!=1)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Danh sách cần có đúng một địa chỉ mặc định.");
    }
    public static void validateAvatar(String avatar){
        if(avatar==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ảnh chưa hợp lệ.");if(avatar.isEmpty())return;
        if(avatar.length()>360000 || !avatar.matches("data:image/(png|jpeg|gif|webp);base64,[A-Za-z0-9+/=]+"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Bạn chọn ảnh PNG/JPEG/GIF/WebP tối đa 256 KB nhé.");
        try{byte[] bytes=Base64.getDecoder().decode(avatar.substring(avatar.indexOf(',')+1));if(bytes.length>262144 || bytes.length<12)throw new IllegalArgumentException();
            String mime=avatar.substring(0,avatar.indexOf(';'));
            boolean valid=mime.endsWith("png") && bytes[0]==(byte)137 && bytes[1]==80 && bytes[2]==78 && bytes[3]==71
              || mime.endsWith("jpeg") && bytes[0]==(byte)255 && bytes[1]==(byte)216 && bytes[2]==(byte)255
              || mime.endsWith("gif") && bytes[0]==71 && bytes[1]==73 && bytes[2]==70
              || mime.endsWith("webp") && bytes[0]==82 && bytes[1]==73 && bytes[2]==70 && bytes[3]==70 && bytes[8]==87 && bytes[9]==69 && bytes[10]==66 && bytes[11]==80;
            if(!valid)throw new IllegalArgumentException();
        }catch(IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Nội dung ảnh chưa hợp lệ.");}
    }
    @Transactional public Map<String,Object> saveAddresses(AddressUpdate input){var user=owner(true);var p=preference(user);revision(p,input.revision);validateAddresses(input.addresses);p.setAddressesJson(encode(input.addresses));return dto(user,preferences.saveAndFlush(p));}
    @Transactional public Map<String,Object> saveAvatar(AvatarUpdate input){var user=owner(true);var p=preference(user);revision(p,input.revision);validateAvatar(input.avatar);p.setAvatar(input.avatar);return dto(user,preferences.saveAndFlush(p));}
    private void addFavorite(User user,Long id){
        Product product=products.findById(id).orElse(null);if(product==null || Boolean.FALSE.equals(product.getStatus()))return;
        if(favorites.findByProductAndUser(product,user)==null){var favorite=new Favorite();favorite.setUser(user);favorite.setProduct(product);favorites.save(favorite);}
    }
    @Transactional public Map<String,Object> wishlist(Long id,boolean add){var user=owner(true);var product=products.findById(id).orElseThrow(()->fail(HttpStatus.NOT_FOUND,"Cuốn sách không còn trong danh mục."));if(add){if(Boolean.FALSE.equals(product.getStatus()))throw fail(HttpStatus.CONFLICT,"Cuốn sách đã ngừng bán.");addFavorite(user,id);}else favorites.findByUser(user).stream().filter(f->f.getProduct()!=null && id.equals(f.getProduct().getProductId())).forEach(favorites::delete);favorites.flush();return dto(user,preference(user));}
    @Transactional public Map<String,Object> migrate(Migration input){
        var user=owner(true);var p=preference(user);Set<String> imports=read(p.getImportsJson(),new TypeReference<Set<String>>(){});
        if(imports.contains(input.migrationKey))return dto(user,p);
        if(imports.size()>=1000)throw fail(HttpStatus.CONFLICT,"Đã đạt giới hạn chuyển dữ liệu. Bạn liên hệ cửa hàng nhé.");
        validateAddresses(input.addresses);validateAvatar(input.avatar);
        var merged=new ArrayList<>(addresses(p));
        for(var legacy:input.addresses){if(merged.stream().anyMatch(a->a.name.equals(legacy.name) && a.phone.equals(legacy.phone) && a.street.equals(legacy.street)))continue;if(merged.size()>=20)throw fail(HttpStatus.CONFLICT,"Số địa chỉ sau chuyển dữ liệu vượt 20. Bạn kiểm tra dữ liệu cũ nhé.");
            boolean empty=merged.isEmpty();Set<Long> ids=new HashSet<>();merged.forEach(a->ids.add(a.id));while(ids.contains(legacy.id))legacy.id++;legacy.defaultAddress=empty || legacy.defaultAddress && merged.stream().noneMatch(a->a.defaultAddress);merged.add(legacy);
        }
        if(!merged.isEmpty() && merged.stream().noneMatch(a->a.defaultAddress))merged.get(0).defaultAddress=true;
        validateAddresses(merged);p.setAddressesJson(encode(merged));if(p.getAvatar().isEmpty())p.setAvatar(input.avatar);
        for(var id:input.wishlistProductIds)if(id!=null && id>0)addFavorite(user,id);
        imports.add(input.migrationKey);p.setImportsJson(encode(imports));favorites.flush();return dto(user,preferences.saveAndFlush(p));
    }
    @Data public static class Address { @Positive private Long id; @NotBlank @Size(max=80) private String name; @NotNull @Size(max=80) private String label="Địa chỉ"; @NotBlank @Size(max=20) private String phone; @NotBlank @Size(min=5,max=255) private String street; @com.fasterxml.jackson.annotation.JsonProperty("isDefault") private boolean defaultAddress; }
    @Data public static class AddressUpdate { @Min(0) private long revision; @NotNull @Size(max=20) @Valid private List<Address> addresses; }
    @Data public static class AvatarUpdate { @Min(0) private long revision; @NotNull @Size(max=360000) private String avatar; }
    @Data public static class Migration { @NotBlank @Pattern(regexp="[A-Za-z0-9-]{10,80}") private String migrationKey; @NotNull @Size(max=20) @Valid private List<Address> addresses=List.of(); @NotNull @Size(max=360000) private String avatar=""; @NotNull @Size(max=500) private List<@Positive Long> wishlistProductIds=List.of(); }
}
