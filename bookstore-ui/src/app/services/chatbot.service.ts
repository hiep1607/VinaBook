import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { ProductService } from './product.service';
import { PAYMENT_METHODS, PaymentMethod } from '../data/payment-methods';
import { environment } from 'src/environments/environment';

export interface ChatMessage {
  from: 'bot' | 'user';
  text: string;
  /** Thẻ sản phẩm kèm theo (nếu có) — bấm vào điều hướng tới /product/:id */
  products?: any[];
  /** Danh sách hình thức thanh toán (khi khách hỏi về thanh toán) */
  payments?: PaymentMethod[];
  /** Gợi ý câu hỏi nhanh (quick replies) */
  chips?: string[];
  /** Nguồn câu trả lời để giao diện báo rõ khi đang chạy chế độ dự phòng. */
  source?: 'ai' | 'local';
  provider?: string;
  fallback?: boolean;
}

interface ChatApiResponse {
  answer: string;
  provider: string;
  model: string;
  fallback: boolean;
  products: any[];
  chips: string[];
}

/** Ngữ cảnh phiên chat để hiểu câu hỏi nối tiếp ("rẻ hơn nữa", "còn cuốn khác"). */
interface ChatContext {
  genreId?: number;
  genreName?: string;
  maxPrice?: number;
  minPrice?: number;
  sort?: 'cheap' | 'expensive' | 'new' | 'best' | 'sale';
  lastProducts?: any[];
  lastIntent?: string;
  offset?: number;
}

/** Một mục kiến thức (FAQ) — chấm điểm theo cụm từ khớp. */
interface FaqEntry {
  id: string;
  /** Cụm từ nhận diện (đã bỏ dấu, thường gồm nhiều chữ để tránh nhầm). */
  patterns: string[];
  answer: string;
  chips?: string[];
}

/**
 * Trợ lý ảo dạng rule-based có chấm điểm ý định (intent scoring), không cần
 * API LLM ngoài. Hiểu tiếng Việt có/không dấu, sai chính tả nhẹ, câu hỏi tự
 * nhiên và câu hỏi nối tiếp nhờ nhớ ngữ cảnh (ChatContext).
 *
 * Luồng xử lý reply():
 *   1. Câu ngắn xã giao (chào / cảm ơn / tạm biệt / đồng ý).
 *   2. Câu nối tiếp dựa trên ngữ cảnh ("rẻ hơn", "còn nữa", "đắt hơn").
 *   3. FAQ thông tin (thanh toán, giao hàng, đổi trả, tài khoản, đơn hàng,
 *      cách mua, liên hệ, giới thiệu) — chấm điểm, mục cao điểm nhất thắng.
 *   4. Truy vấn sản phẩm: lọc kết hợp thể loại + khoảng giá + sắp xếp, hoặc
 *      tra theo tác giả / tên sách.
 *   5. Không khớp → gợi ý cách hỏi.
 */
@Injectable({ providedIn: 'root' })
export class ChatbotService {

  private catalog: any[] = [];
  private ctx: ChatContext = {};

  constructor(
    private productService: ProductService,
    private http: HttpClient
  ) {
    this.productService.getProducts().subscribe(list => {
      this.catalog = list || [];
    });
  }

  /**
   * Luồng trả lời chính: gửi hội thoại tới backend để API key không bao giờ nằm
   * trong bundle Angular. Nếu backend hoặc nhà cung cấp AI lỗi, bộ luật nội bộ
   * hiện có tiếp quản ngay để khung chat vẫn sử dụng được.
   */
  ask(input: string, history: ChatMessage[]): Observable<ChatMessage> {
    const turns = history
      .filter(message => message.text && (message.from === 'user' || message.from === 'bot'))
      .slice(-8)
      .map(message => ({
        role: message.from === 'user' ? 'user' : 'assistant',
        content: message.text
      }));

    return this.http.post<ChatApiResponse>(environment.apiUrl + '/api/chat', {
      message: input,
      history: turns
    }).pipe(
      map(response => ({
        from: 'bot' as const,
        text: response.answer,
        products: response.products || [],
        chips: response.chips || [],
        source: response.fallback ? 'local' as const : 'ai' as const,
        provider: response.provider,
        fallback: response.fallback
      })),
      catchError(() => {
        const fallback = this.reply(input);
        return of({
          ...fallback,
          source: 'local' as const,
          provider: 'local',
          fallback: true
        });
      })
    );
  }

  // ==================================================================
  // API công khai
  // ==================================================================

  /** Lời chào mở đầu khi mở khung chat. */
  greeting(): ChatMessage {
    return {
      from: 'bot',
      text:
        'Chào bạn, mình là trợ lý VinaBook. Mình có thể gợi ý sách từ catalog hiện tại, tìm theo thể loại, tác giả, mức giá hoặc giải đáp về giao hàng và thanh toán.',
      chips: [
        'Gợi ý sách hay',
        'Sách đang giảm giá',
        'Phí giao hàng thế nào?',
        'Có những thể loại nào?'
      ],
      source: 'local'
    };
  }

  /** Sinh câu trả lời cho một câu hỏi của người dùng. */
  reply(input: string): ChatMessage {
    const raw = (input || '').trim();
    const q = this.normalize(raw);

    if (!q) {
      return this.fallback();
    }

    // 1) Xã giao (câu ngắn)
    const small = this.smallTalk(q);
    if (small) {
      return small;
    }

    // 2) Câu nối tiếp dựa vào ngữ cảnh phiên trước
    const follow = this.followUp(q);
    if (follow) {
      return follow;
    }

    // 3) FAQ thông tin — chấm điểm, mục cao điểm nhất thắng
    const faq = this.bestFaq(q);

    // 4) Truy vấn sản phẩm (thể loại / giá / sắp xếp / tác giả / tên)
    const product = this.productQuery(raw, q);

    // Ưu tiên: FAQ khớp rõ (>=2 điểm) thắng; nếu không có sản phẩm thì FAQ
    // yếu vẫn thắng; còn lại ưu tiên sản phẩm.
    if (faq && (faq.score >= 2 || !product)) {
      return this.faqMessage(faq.entry);
    }
    if (product) {
      return product;
    }
    if (faq) {
      return this.faqMessage(faq.entry);
    }

    // 5) Không khớp
    return this.fallback(raw);
  }

  // ==================================================================
  // 1. Xã giao
  // ==================================================================

  private smallTalk(q: string): ChatMessage | null {
    if (q.length <= 16 && this.hasAny(q, ['xin chao', 'chao ban', 'chao', 'hello',
      'hi', 'hey', 'alo', 'co do khong', 'co do ko'])) {
      return this.bot('Chào bạn! 👋 Mình có thể gợi ý sách hoặc giúp bạn tìm ' +
        'sách theo thể loại, tác giả, giá. Bạn đang tìm gì nào?',
        ['Gợi ý sách hay', 'Sách mới', 'Sách giảm giá']);
    }

    if (this.hasAny(q, ['cam on', 'cam ta', 'thanks', 'thank you', 'tks', 'thank'])) {
      return this.bot('Rất vui được giúp bạn! 😊 Chúc bạn tìm được cuốn sách ưng ý.',
        ['Gợi ý sách hay', 'Sách đang giảm giá']);
    }

    if (this.hasAny(q, ['tam biet', 'bye', 'goodbye', 'hen gap lai', 'di day', 'di nhe'])) {
      return this.bot('Tạm biệt bạn nhé! 👋 Hẹn gặp lại ở VinaBook 📚');
    }

    if (this.hasAny(q, ['ban la ai', 'ban ten gi', 'ban la gi', 'ai day', 'la bot a',
      'la nguoi that', 'la ai'])) {
      return this.bot('Mình là trợ lý ảo của VinaBook 🤖. Mình giúp bạn tìm sách, ' +
        'gợi ý theo sở thích và giải đáp về mua hàng, giao nhận, thanh toán. ' +
        'Bạn cần mình giúp gì nào?',
        ['Gợi ý sách hay', 'Bạn làm được gì?']);
    }

    if (this.hasAny(q, ['ban lam duoc gi', 'lam duoc gi', 'giup duoc gi', 'co the lam gi',
      'giup gi', 'huong dan', 'help', 'chuc nang'])) {
      return this.helpMessage();
    }

    return null;
  }

  // ==================================================================
  // 2. Câu nối tiếp dựa trên ngữ cảnh
  // ==================================================================

  private followUp(q: string): ChatMessage | null {
    if (this.catalog.length === 0) {
      return null;
    }

    // "rẻ hơn (nữa)", "có cuốn nào rẻ hơn"
    if (this.hasAny(q, ['re hon', 're nua', 'gia thap hon', 'bot tien', 'it tien hon'])
      && (this.ctx.lastProducts || this.ctx.genreId !== undefined)) {
      const base = this.applyContextFilter();
      const cheaper = [...base].sort((a, b) => Number(a.price) - Number(b.price)).slice(0, 4);
      return this.showProducts('Đây là những cuốn có giá tốt hơn nữa 💰', cheaper,
        ['Còn cuốn khác không?', 'Sách bán chạy']);
    }

    // "đắt hơn", "cao cấp hơn"
    if (this.hasAny(q, ['dat hon', 'mac hon', 'cao cap hon', 'xin hon'])
      && (this.ctx.lastProducts || this.ctx.genreId !== undefined)) {
      const base = this.applyContextFilter();
      const pricier = [...base].sort((a, b) => Number(b.price) - Number(a.price)).slice(0, 4);
      return this.showProducts('Vài lựa chọn cao cấp hơn cho bạn ✨', pricier,
        ['Rẻ hơn', 'Sách bán chạy']);
    }

    // "còn nữa không", "cuốn khác", "thêm đi", "xem thêm"
    if (this.hasAny(q, ['con nua', 'con khong', 'con ko', 'cuon khac', 'quyen khac',
      'them di', 'xem them', 'khac di', 'nua di', 'con gi nua'])
      && this.ctx.lastProducts) {
      const base = this.applyContextFilter(true);
      const offset = this.ctx.offset || 0;
      const more = base.slice(offset, offset + 4);
      if (more.length === 0) {
        return this.bot('Mình đã gợi ý hết những cuốn phù hợp rồi 😅 ' +
          'Bạn thử một thể loại hoặc khoảng giá khác nhé!',
          ['Gợi ý sách hay', 'Có những thể loại nào?']);
      }
      this.ctx.offset = offset + 4;
      this.ctx.lastProducts = more;
      return this.showProducts('Thêm vài cuốn nữa cho bạn 👇', more, undefined, false);
    }

    return null;
  }

  /** Lấy danh sách sản phẩm theo bộ lọc ngữ cảnh hiện tại (thể loại + giá). */
  private applyContextFilter(fullSort = false): any[] {
    let list = [...this.catalog];
    if (this.ctx.genreId !== undefined) {
      list = list.filter(p => p.categoryId === this.ctx.genreId);
    }
    if (this.ctx.maxPrice !== undefined) {
      list = list.filter(p => Number(p.price) <= (this.ctx.maxPrice as number));
    }
    if (this.ctx.minPrice !== undefined) {
      list = list.filter(p => Number(p.price) >= (this.ctx.minPrice as number));
    }
    if (fullSort && this.ctx.sort === 'best') {
      list.sort((a, b) => (b.sold || 0) - (a.sold || 0));
    }
    return list;
  }

  // ==================================================================
  // 3. FAQ thông tin — chấm điểm
  // ==================================================================

  private readonly faqs: FaqEntry[] = [
    {
      id: 'shipping',
      patterns: ['giao hang', 'ship', 'van chuyen', 'phi ship', 'phi giao', 'phi van chuyen',
        'bao lau nhan', 'may ngay nhan', 'khi nao nhan', 'giao den', 'giao toi',
        'freeship', 'mien phi giao', 'nhan hang', 'thoi gian giao'],
      answer:
        'Về giao hàng 🚚\n' +
        '• Thời gian giao dự kiến: 2 đến 4 ngày làm việc tùy khu vực.\n' +
        '• Phí ship 20.000đ, MIỄN PHÍ cho đơn từ 300.000đ.\n' +
        '• Bạn có thể chọn thanh toán khi nhận hàng (COD).',
      chips: ['Có những cách thanh toán nào?', 'Chính sách đổi trả']
    },
    {
      id: 'return',
      patterns: ['doi tra', 'tra hang', 'hoan tien', 'hoan hang', 'bao hanh', 'sach loi',
        'sach hong', 'sach rach', 'giao sai', 'khong ung y', 'tra lai', 'refund'],
      answer:
        'Chính sách đổi trả 🔁\n' +
        '• Đổi/trả trong 7 ngày nếu sách lỗi in, rách, hoặc giao sai.\n' +
        '• Sách phải còn nguyên vẹn, chưa qua sử dụng.\n' +
        '• Hoàn tiền trong 3-5 ngày sau khi nhận hàng trả về.',
      chips: ['Phí giao hàng thế nào?', 'Liên hệ hỗ trợ']
    },
    {
      id: 'order-track',
      patterns: ['kiem tra don', 'tra cuu don', 'don hang cua toi', 'theo doi don',
        'don cua toi', 'trang thai don', 'don da giao chua', 'xem don hang', 'lich su don'],
      answer:
        'Theo dõi đơn hàng 📦\n' +
        'Bạn đăng nhập rồi vào mục "Đơn hàng" (hoặc trang cá nhân) để xem trạng ' +
        'thái: Chờ xử lý → Đang giao → Hoàn thành. Trạng thái được cập nhật realtime.',
      chips: ['Đăng nhập thế nào?', 'Phí giao hàng thế nào?']
    },
    {
      id: 'account',
      patterns: ['dang ky', 'dang nhap', 'tao tai khoan', 'quen mat khau', 'doi mat khau',
        'tai khoan cua toi', 'dang xuat', 'cap nhat thong tin', 'ho so', 'profile'],
      answer:
        'Tài khoản 👤\n' +
        '• Đăng ký/đăng nhập bằng email ở góc trên bên phải.\n' +
        '• Đổi mật khẩu, cập nhật thông tin & địa chỉ trong trang cá nhân.\n' +
        '• Có tài khoản bạn sẽ lưu được sổ địa chỉ và xem lại lịch sử đơn hàng.',
      chips: ['Theo dõi đơn hàng', 'Cách đặt hàng?']
    },
    {
      id: 'how-to-buy',
      patterns: ['cach mua', 'cach dat hang', 'dat hang the nao', 'mua the nao', 'lam sao mua',
        'lam sao dat', 'quy trinh mua', 'dat sach', 'mua sach the nao', 'checkout'],
      answer:
        'Cách đặt hàng 🛒\n' +
        '1. Bấm "Thêm vào giỏ" ở trang sách (hoặc "Mua ngay").\n' +
        '2. Vào giỏ hàng, kiểm tra rồi bấm "Thanh toán".\n' +
        '3. Nhập/chọn địa chỉ giao, chọn hình thức thanh toán và xác nhận.\n' +
        'Xong! Đơn sẽ hiện trong mục "Đơn hàng".',
      chips: ['Có những cách thanh toán nào?', 'Phí giao hàng thế nào?']
    },
    {
      id: 'contact',
      patterns: ['lien he', 'hotline', 'so dien thoai', 'sdt', 'goi cho', 'dia chi cua hang',
        'cua hang o dau', 'shop o dau', 'gio mo cua', 'gio lam viec', 'email ho tro',
        'ho tro khach hang', 'cham soc khach hang'],
      answer:
        'Liên hệ VinaBook ☎️\n' +
        '• Email: hotro@vinabook.vn\n' +
        '• Bạn cũng có thể để lại lời nhắn ở trang "Liên hệ".',
      chips: ['Chính sách đổi trả', 'Phí giao hàng thế nào?']
    },
    {
      id: 'about',
      patterns: ['gioi thieu', 've vinabook', 'vinabook la gi', 'shop ban gi', 'ban sach gi',
        'cua hang gi', 'co uy tin khong', 'sach that khong', 'hang that'],
      answer:
        'VinaBook 📚 là nhà sách trực tuyến với hàng chục đầu sách chính hãng thuộc ' +
        'nhiều thể loại: văn học, kinh tế, thiếu nhi, kỹ năng… Cam kết sách thật, ' +
        'giá tốt, giao nhanh. Bạn muốn mình gợi ý vài cuốn hay không?',
      chips: ['Gợi ý sách hay', 'Có những thể loại nào?']
    }
  ];

  /** Chấm điểm mọi FAQ, trả về mục cao điểm nhất (nếu có khớp). */
  private bestFaq(q: string): { entry: FaqEntry; score: number } | null {
    let best: { entry: FaqEntry; score: number } | null = null;
    for (const entry of this.faqs) {
      let score = 0;
      for (const p of entry.patterns) {
        if (q.includes(this.normalize(p))) {
          // cụm nhiều chữ có trọng số cao hơn
          score += p.includes(' ') ? 2 : 1;
        }
      }
      if (score > 0 && (!best || score > best.score)) {
        best = { entry, score };
      }
    }
    return best;
  }

  private faqMessage(entry: FaqEntry): ChatMessage {
    this.ctx.lastIntent = 'faq:' + entry.id;
    return this.bot(entry.answer, entry.chips);
  }

  // ==================================================================
  // 4. Truy vấn sản phẩm
  // ==================================================================

  private productQuery(raw: string, q: string): ChatMessage | null {
    if (this.catalog.length === 0) {
      return this.bot('Mình đang tải dữ liệu sách, bạn thử lại sau giây lát nhé ⏳');
    }

    // Câu hỏi dữ liệu trực tiếp về một tên sách cụ thể phải được trả lời trước
    // các intent thống kê chung. Đây cũng là lớp dự phòng khi backend mất kết nối.
    const mentioned = this.catalog.find(p => {
      const name = this.normalize(p.name);
      return name.length >= 3 && q.includes(name);
    });
    if (mentioned && this.hasAny(q, ['con bao nhieu', 'bao nhieu ban', 'bao nhieu cuon',
      'con may', 'ton kho', 'so luong con', 'con hang', 'het hang'])) {
      const quantity = Number(mentioned.quantity || 0);
      return this.showProducts(
        quantity > 0
          ? `Theo dữ liệu hiện tại, “${mentioned.name}” còn ${quantity} cuốn. Số lượng có thể thay đổi khi có đơn hàng mới.`
          : `Theo dữ liệu hiện tại, “${mentioned.name}” đang hết hàng. Bạn có thể kiểm tra lại sau vì tồn kho có thể thay đổi.`,
        [mentioned]
      );
    }
    if (mentioned && this.hasAny(q, ['gia bao nhieu', 'bao nhieu tien', 'gia cua', 'gia sach'])) {
      return this.showProducts(
        `“${mentioned.name}” hiện có giá ${this.money(mentioned.price)}${mentioned.discount > 0 ? ` sau khi giảm ${mentioned.discount}%` : ''}.`,
        [mentioned]
      );
    }

    // 4.0) Thanh toán (ưu tiên cao, có danh sách phương thức)
    if (this.hasAny(q, ['thanh toan', 'tra tien', 'tra gop', 'chuyen khoan', 'cod',
      'momo', 'vnpay', 'the tin dung', 'visa', 'master', 'quet ma', 'vietqr', 'payment'])) {
      this.ctx.lastIntent = 'payment';
      return {
        from: 'bot',
        text: 'VinaBook luôn hỗ trợ COD. VietQR, VNPay Sandbox và MoMo Test sẽ hiện ở bước thanh toán khi cửa hàng đã cấu hình cổng tương ứng.',
        payments: PAYMENT_METHODS,
        chips: ['Gợi ý sách hay', 'Phí giao hàng thế nào?']
      };
    }

    // 4.1) Liệt kê thể loại
    if (this.hasAny(q, ['co nhung the loai', 'nhung the loai nao', 'the loai gi',
      'danh muc nao', 'co loai sach nao', 'ban nhung loai', 'the loai sach nao',
      'liet ke the loai'])) {
      const cats = this.categoryNames();
      this.ctx.lastIntent = 'genres';
      return this.bot('VinaBook hiện có các thể loại 📖:\n• ' + cats.join('\n• ') +
        '\n\nBạn muốn xem thể loại nào?',
        cats.slice(0, 4).map(c => 'Sách ' + c.toLowerCase()));
    }

    // 4.2) Thống kê số lượng
    if (this.hasAny(q, ['bao nhieu cuon', 'bao nhieu sach', 'bao nhieu dau sach',
      'co may cuon', 'so luong sach', 'tong cong bao nhieu'])) {
      const nCat = this.categoryNames().length;
      return this.bot(`Hiện VinaBook có ${this.catalog.length} đầu sách thuộc ` +
        `${nCat} thể loại 📚. Bạn muốn mình gợi ý vài cuốn nổi bật không?`,
        ['Sách bán chạy', 'Sách mới', 'Có những thể loại nào?']);
    }

    // 4.3) Phân tích thực thể: khoảng giá, thể loại, sắp xếp
    const price = this.extractPriceRange(q);
    const genre = this.matchGenre(q);
    const sort = this.matchSort(q);

    const wantsSale = this.hasAny(q, ['giam gia', 'khuyen mai', 'sale', 'dang giam', 'uu dai', 'giam manh']);
    const wantsNew = this.hasAny(q, ['sach moi', 'moi nhat', 'moi ra', 'hang moi', 'moi ve', 'new']);
    const wantsBest = this.hasAny(q, ['ban chay', 'best seller', 'bestseller', 'mua nhieu', 'hot nhat', 'noi bat']);
    const wantsRec = this.hasAny(q, ['goi y', 'sach hay', 'nen doc', 'nen mua', 'hay nhat',
      'doc gi', 'pho bien', 'recommend', 'tu van', 'sach nao hay', 'gioi thieu sach']);

    const topicCue = this.hasAny(q, ['chu de', 'noi dung', 'sach ve', 'cuon ve', 'phieu luu',
      'giao tiep', 'phat trien ban than', 'tre em', 'tre nho', 'thieu nhi', 'kinh doanh',
      'khoi nghiep', 'trinh tham', 'pha an', 'tinh yeu', 'lang man']);
    if (topicCue) {
      const topicResults = this.searchCatalog(raw);
      if (topicResults.length > 0) {
        this.remember({ lastProducts: topicResults.slice(0, 4), lastIntent: 'topic', offset: 4 });
        return this.showProducts(
          `Mình tìm thấy ${topicResults.length} cuốn gần với nhu cầu của bạn:`,
          topicResults.slice(0, 4),
          ['Còn cuốn khác không?', 'Sách rẻ hơn']);
      }
    }

    // Nếu có bất kỳ tín hiệu lọc sản phẩm nào → dựng truy vấn kết hợp
    const hasFilter = !!genre || !!price || !!sort || wantsSale || wantsNew || wantsBest || wantsRec;
    if (hasFilter) {
      return this.buildFilteredResult({ price, genre, sort, wantsSale, wantsNew, wantsBest });
    }

    // 4.4) Tra theo tác giả
    const byAuthor = this.matchAuthor(q);
    if (byAuthor.length > 0) {
      this.remember({ lastProducts: byAuthor.slice(0, 4), lastIntent: 'author', offset: 4 });
      return this.showProducts(
        `Sách của tác giả bạn tìm 🖋️ (${byAuthor.length} cuốn):`,
        byAuthor.slice(0, 4),
        ['Sách bán chạy', 'Có những thể loại nào?']);
    }

    // 4.5) Tra theo tên sách / từ khóa
    const found = this.searchCatalog(raw);
    if (found.length > 0) {
      this.remember({ lastProducts: found.slice(0, 4), lastIntent: 'search', offset: 4 });
      return this.showProducts(
        `Mình tìm thấy ${found.length} cuốn liên quan đến "${raw.trim()}" 🔎`,
        found.slice(0, 4),
        ['Gợi ý sách hay', 'Có những cách thanh toán nào?']);
    }

    return null;
  }

  /** Dựng kết quả lọc kết hợp thể loại + giá + sắp xếp + khuyến mãi/mới/bán chạy. */
  private buildFilteredResult(opts: {
    price: { min?: number; max?: number } | null;
    genre: { id: number; name: string } | null;
    sort: ChatContext['sort'] | null;
    wantsSale: boolean;
    wantsNew: boolean;
    wantsBest: boolean;
  }): ChatMessage {
    const { price, genre, wantsSale, wantsNew, wantsBest } = opts;
    let list = [...this.catalog];
    const labels: string[] = [];

    if (genre) {
      list = list.filter(p => p.categoryId === genre.id);
      labels.push('thể loại ' + genre.name);
    }
    if (wantsSale) {
      list = list.filter(p => p.discount > 0);
      labels.push('đang giảm giá');
    }
    if (price?.max !== undefined) {
      list = list.filter(p => Number(p.price) <= (price.max as number));
      labels.push('giá dưới ' + this.money(price.max));
    }
    if (price?.min !== undefined) {
      list = list.filter(p => Number(p.price) >= (price.min as number));
      labels.push('giá trên ' + this.money(price.min));
    }

    // Sắp xếp
    const sort = opts.sort
      || (wantsSale ? 'sale' : wantsNew ? 'new' : wantsBest ? 'best' : null);
    switch (sort) {
      case 'cheap': list.sort((a, b) => Number(a.price) - Number(b.price)); break;
      case 'expensive': list.sort((a, b) => Number(b.price) - Number(a.price)); break;
      case 'new': list.sort((a, b) => (b.id || 0) - (a.id || 0)); break;
      case 'best': list.sort((a, b) => (b.sold || 0) - (a.sold || 0)); break;
      case 'sale': list.sort((a, b) => (b.discount || 0) - (a.discount || 0)); break;
      default: list.sort((a, b) => (b.sold || 0) - (a.sold || 0));
    }

    this.remember({
      genreId: genre?.id,
      genreName: genre?.name,
      maxPrice: price?.max,
      minPrice: price?.min,
      sort: sort || 'best',
      lastProducts: list.slice(0, 4),
      lastIntent: 'filter',
      offset: 4
    });

    if (list.length === 0) {
      // Nới điều kiện: bỏ giá, giữ thể loại nếu có
      const fallbackList = genre
        ? this.catalog.filter(p => p.categoryId === genre.id)
        : this.bestSellers(4);
      const desc = labels.join(', ');
      return this.showProducts(
        `Chưa có cuốn nào khớp "${desc}" 😅 Đây là vài gợi ý gần nhất:`,
        fallbackList.slice(0, 4),
        ['Sách bán chạy', 'Sách giảm giá']);
    }

    const head = labels.length
      ? `Sách ${labels.join(', ')} 👇`
      : sort === 'new' ? 'Một vài đầu sách mới nhất tại VinaBook 🆕'
      : sort === 'sale' ? 'Những cuốn đang giảm giá sâu nhất 🔥'
      : 'Đây là những cuốn được mua nhiều nhất, bạn tham khảo nhé 🌟';

    return this.showProducts(head, list.slice(0, 4),
      ['Còn cuốn khác không?', 'Rẻ hơn', 'Có những cách thanh toán nào?']);
  }

  // ==================================================================
  // Trích thực thể
  // ==================================================================

  /** Danh sách thể loại duy nhất kèm categoryId, lấy từ catalog cache. */
  private categories(): { id: number; name: string }[] {
    const seen = new Map<number, string>();
    for (const p of this.catalog) {
      if (p.categoryId != null && !seen.has(p.categoryId)) {
        seen.set(p.categoryId, p.category);
      }
    }
    return Array.from(seen, ([id, name]) => ({ id, name }));
  }

  private categoryNames(): string[] {
    return this.categories().map(c => c.name);
  }

  /** Tìm thể loại mà tên (bỏ dấu) xuất hiện trong câu hỏi. */
  private matchGenre(q: string): { id: number; name: string } | null {
    let best: { id: number; name: string; len: number } | null = null;
    for (const c of this.categories()) {
      const norm = this.normalize(c.name);
      if (norm && q.includes(norm) && (!best || norm.length > best.len)) {
        best = { id: c.id, name: c.name, len: norm.length };
      }
    }
    return best ? { id: best.id, name: best.name } : null;
  }

  /** Nhận diện yêu cầu sắp xếp: rẻ nhất / đắt nhất / mới / bán chạy. */
  private matchSort(q: string): ChatContext['sort'] | null {
    if (this.hasAny(q, ['re nhat', 'gia re', 'gia thap', 'tiet kiem', 'tien it', 're tien'])) {
      return 'cheap';
    }
    if (this.hasAny(q, ['dat nhat', 'mac nhat', 'cao cap', 'sang chanh', 'gia cao'])) {
      return 'expensive';
    }
    return null;
  }

  /** Tra sách theo tác giả (khớp trên trường author, bỏ dấu). */
  private matchAuthor(q: string): any[] {
    if (!this.hasAny(q, ['tac gia', 'cua ', 'viet boi', 'sang tac', 'author'])) {
      return [];
    }
    const cleaned = q
      .replace(/.*\b(tac gia|cua|viet boi|sang tac|author)\b/, '')
      .replace(/\b(sach|cuon|quyen|nao|gi|khong|co)\b/g, '')
      .trim();
    if (!cleaned) {
      return [];
    }
    return this.catalog.filter(p => this.normalize(p.author).includes(cleaned));
  }

  /**
   * Trích khoảng giá: "dưới 100k", "trên 200k", "từ 100k đến 300k",
   * "khoảng 150 nghìn", "150000". Trả {min?, max?} hoặc null.
   */
  private extractPriceRange(q: string): { min?: number; max?: number } | null {
    const amounts = this.parseAmounts(q);
    if (amounts.length === 0) {
      return null;
    }

    const hasUnder = /(duoi|nho hon|it hon|toi da|khong qua|<|be hon)/.test(q);
    const hasOver = /(tren|hon|lon hon|tu|it nhat|toi thieu|>)/.test(q);
    const hasRange = /(tu .* den|den|-|khoang|giua)/.test(q) && amounts.length >= 2;

    if (hasRange && amounts.length >= 2) {
      const sorted = [...amounts].sort((a, b) => a - b);
      return { min: sorted[0], max: sorted[sorted.length - 1] };
    }
    if (hasUnder) {
      return { max: amounts[0] };
    }
    if (hasOver && !hasUnder) {
      return { min: amounts[0] };
    }
    // Có nêu số tiền nhưng không rõ hướng → coi như trần giá
    return { max: amounts[0] };
  }

  /** Tìm mọi số tiền trong câu, quy đổi đơn vị k/nghìn/triệu về đồng. */
  private parseAmounts(q: string): number[] {
    const out: number[] = [];
    const re = /(\d[\d.,]*)\s*(trieu|tr|k|nghin|ngan|d|dong|vnd)?/g;
    let m: RegExpExecArray | null;
    while ((m = re.exec(q)) !== null) {
      let value = parseFloat(m[1].replace(/[.,]/g, ''));
      if (isNaN(value) || value <= 0) {
        continue;
      }
      const unit = m[2];
      if (unit === 'trieu' || unit === 'tr') {
        value *= 1000000;
      } else if (unit === 'k' || unit === 'nghin' || unit === 'ngan') {
        value *= 1000;
      } else if (value < 1000) {
        // "100" trong ngữ cảnh giá sách hầu như là 100k
        value *= 1000;
      }
      out.push(value);
    }
    return out;
  }

  // ==================================================================
  // Tìm kiếm & tiện ích
  // ==================================================================

  private bestSellers(n: number): any[] {
    return [...this.catalog].sort((a, b) => (b.sold || 0) - (a.sold || 0)).slice(0, n);
  }

  /** Tìm theo tên / tác giả / thể loại, không phân biệt dấu. */
  private searchCatalog(keyword: string): any[] {
    const norm = this.normalize(keyword);
    const ignored = new Set([
      'sach', 'cuon', 'quyen', 'tua', 've', 'cua', 'tac', 'gia', 'the', 'loai',
      'tim', 'mua', 'co', 'khong', 'nao', 'gi', 'toi', 'minh', 'muon', 'can',
      'giup', 'vai', 'mot', 'nhung', 'chu', 'de', 'cho', 'doc', 'hay', 'luu'
    ]);
    const tokens = norm.split(/\s+/)
      .filter(token => token.length > 1 && !ignored.has(token));
    if (tokens.length === 0) {
      return [];
    }

    return this.catalog
      .map(product => {
        const name = this.normalize(product.name);
        const author = this.normalize(product.author);
        const category = this.normalize(product.category);
        const description = this.normalize(product.description || '');
        let semanticBoost = norm.includes('phieu luu') &&
          [name, description].some(value => this.hasAny(value,
            ['phieu luu', 'hanh trinh', 'kham pha', 'du ky'])) ? 12 : 0;
        if (this.hasAny(norm, ['tre em', 'tre nho']) && category.includes('thieu nhi')) {
          semanticBoost += 12;
        }
        if (norm.includes('phat trien ban than') && this.hasAny(name + ' ' + category + ' ' + description,
          ['ky nang', 'tam ly', 'thoi quen'])) {
          semanticBoost += 12;
        }
        if (this.hasAny(norm, ['giao tiep', 'ung xu']) && this.hasAny(name + ' ' + description,
          ['giao tiep', 'ung xu', 'doi nhan xu the'])) {
          semanticBoost += 12;
        }
        const score = tokens.reduce((total, token) => total
          + (name.includes(token) ? 8 : 0)
          + (author.includes(token) ? 6 : 0)
          + (category.includes(token) ? 5 : 0)
          + (description.includes(token) ? 2 : 0), semanticBoost);
        return { product, score };
      })
      .filter(item => item.score > 0)
      .sort((a, b) => b.score - a.score || (b.product.sold || 0) - (a.product.sold || 0))
      .map(item => item.product);
  }

  private helpMessage(): ChatMessage {
    return this.bot(
      'Mình có thể giúp bạn 🤝:\n' +
      '• Gợi ý sách bán chạy / sách hay / sách mới\n' +
      '• Tìm theo thể loại, tác giả hoặc tên sách\n' +
      '• Lọc theo giá (vd: "sách thiếu nhi dưới 100k")\n' +
      '• Giải đáp: giao hàng, thanh toán, đổi trả, tài khoản, cách đặt hàng\n' +
      'Bạn cứ nhập câu hỏi tự nhiên nhé!',
      ['Gợi ý sách hay', 'Phí giao hàng thế nào?', 'Có những thể loại nào?']);
  }

  private fallback(raw: string = ''): ChatMessage {
    return this.bot(
      raw
        ? `Xin lỗi, mình chưa hiểu rõ ý "${raw.trim()}" 😅\n` +
          'Bạn thử hỏi theo thể loại (vd: "sách kinh tế"), tác giả, khoảng giá, ' +
          'hoặc về giao hàng / thanh toán / đổi trả nhé.'
        : 'Bạn có thể hỏi mình về sách, giao hàng hoặc cách thanh toán nhé!',
      ['Gợi ý sách hay', 'Có những thể loại nào?', 'Phí giao hàng thế nào?']);
  }

  // ---- primitives ----

  /** Bỏ dấu tiếng Việt để khớp không phân biệt dấu. */
  private normalize(str: string): string {
    return (str || '')
      .normalize('NFD')
      .replace(/[̀-ͯ]/g, '')
      .replace(/đ/g, 'd')
      .replace(/Đ/g, 'D')
      .toLowerCase();
  }

  private hasAny(q: string, keywords: string[]): boolean {
    return keywords.some(k => q.includes(this.normalize(k)));
  }

  private money(v?: number): string {
    return (v || 0).toLocaleString('vi-VN') + 'đ';
  }

  private bot(text: string, chips?: string[]): ChatMessage {
    return { from: 'bot', text, chips };
  }

  private showProducts(text: string, products: any[], chips?: string[], withChips = true): ChatMessage {
    return { from: 'bot', text, products, chips: withChips ? chips : undefined };
  }

  private remember(patch: Partial<ChatContext>): void {
    this.ctx = { ...this.ctx, ...patch };
  }
}
