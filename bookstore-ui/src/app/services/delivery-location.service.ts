import { HttpBackend, HttpClient } from '@angular/common/http';
import { Inject, Injectable, InjectionToken } from '@angular/core';
import { timeout } from 'rxjs/operators';

export interface AddressArea { code: number; name: string; province_code?: number; }
export interface LocatedAddress { street: string; accuracy: number; }
interface PhotonPlace {
  countrycode?: string; street?: string; name?: string; district?: string;
  locality?: string; city?: string; county?: string; state?: string;
}
export const DELIVERY_LOCATION_API = new InjectionToken<{ areas: string; reverse: string }>(
  'DELIVERY_LOCATION_API', { providedIn: 'root', factory: () => ({
    areas: 'https://provinces.open-api.vn/api/v2', reverse: 'https://photon.komoot.io/reverse'
  }) }
);

@Injectable({ providedIn: 'root' })
export class DeliveryLocationService {
  // Third-party requests must bypass the app's JWT and ngrok interceptors.
  private http: HttpClient;
  private provinceCache?: AddressArea[];
  private wardCache = new Map<number, AddressArea[]>();
  private provincesRequest?: Promise<AddressArea[]>;
  private wardRequests = new Map<number, Promise<AddressArea[]>>();
  private lastLookup = 0;

  constructor(backend: HttpBackend, @Inject(DELIVERY_LOCATION_API) private api: { areas: string; reverse: string }) {
    this.http = new HttpClient(backend);
  }

  provinces(): Promise<AddressArea[]> {
    if (this.provinceCache) return Promise.resolve(this.provinceCache);
    if (this.provincesRequest) return this.provincesRequest;
    this.provincesRequest = this.http.get<AddressArea[]>(this.api.areas + '/p/').pipe(timeout(12000)).toPromise()
      .then(rows => this.provinceCache = this.areas(rows))
      .finally(() => this.provincesRequest = undefined);
    return this.provincesRequest;
  }

  wards(province: number): Promise<AddressArea[]> {
    const cached = this.wardCache.get(province);
    if (cached) return Promise.resolve(cached);
    const pending = this.wardRequests.get(province);
    if (pending) return pending;
    const request = this.http.get<{ wards: AddressArea[] }>(this.api.areas + '/p/' + province, { params: { depth: '2' } })
      .pipe(timeout(12000)).toPromise().then(data => {
        const rows = this.areas(data.wards).filter(area => area.province_code === province);
        if (!rows.length) throw new Error('Chưa có danh sách phường/xã. Bạn nhập địa chỉ đầy đủ nhé.');
        this.wardCache.set(province, rows);
        return rows;
      }).finally(() => this.wardRequests.delete(province));
    this.wardRequests.set(province, request);
    return request;
  }

  private areas(rows: AddressArea[]): AddressArea[] {
    if (!Array.isArray(rows) || !rows.length) throw new Error('Chưa tải được danh sách khu vực.');
    const valid = rows.filter(row => Number.isInteger(row.code) && typeof row.name === 'string' && !!row.name.trim())
      .sort((a, b) => a.name.localeCompare(b.name, 'vi'));
    if (!valid.length) throw new Error('Chưa tải được danh sách khu vực.');
    return valid;
  }

  position(): Promise<GeolocationPosition> {
    if (!window.isSecureContext) return Promise.reject(new Error('Định vị cần kết nối HTTPS. Bạn có thể nhập địa chỉ thủ công.'));
    if (!navigator.geolocation) return Promise.reject(new Error('Thiết bị chưa hỗ trợ định vị. Bạn nhập địa chỉ thủ công nhé.'));
    return new Promise((resolve, reject) => {
      let settled = false;
      const finish = (position?: GeolocationPosition, error?: string) => {
        if (settled) return;
        settled = true; clearTimeout(deadline);
        position ? resolve(position) : reject(new Error(error));
      };
      // Also bounds browsers that leave a permission prompt unanswered.
      const deadline = setTimeout(() => finish(undefined, 'Chưa nhận được vị trí. Bạn bật quyền vị trí rồi thử lại, hoặc nhập địa chỉ.'), 18000);
      navigator.geolocation.getCurrentPosition(position => finish(position), error => finish(undefined,
        error.code === 1 ? 'Bạn chưa cho phép truy cập vị trí. Bạn có thể bật quyền trong trình duyệt hoặc nhập địa chỉ.' :
        error.code === 3 ? 'Lấy vị trí quá lâu. Bạn thử lại hoặc nhập địa chỉ thủ công.' :
        'Chưa xác định được vị trí. Bạn kiểm tra định vị trên thiết bị hoặc nhập địa chỉ.'),
        { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 });
    });
  }

  async reverse(position: GeolocationPosition): Promise<LocatedAddress> {
    const { latitude, longitude, accuracy } = position.coords;
    if (!Number.isFinite(latitude) || !Number.isFinite(longitude) || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) {
      throw new Error('Vị trí không hợp lệ. Bạn thử lại hoặc nhập địa chỉ.');
    }
    if (Date.now() - this.lastLookup < 1500) throw new Error('Bạn chờ một chút trước khi lấy lại vị trí nhé.');
    this.lastLookup = Date.now();
    const data = await this.http.get<{ features: Array<{ properties: PhotonPlace }> }>(this.api.reverse, {
      params: { lat: String(latitude), lon: String(longitude), limit: '1', radius: '1', lang: 'default' }
    }).pipe(timeout(12000)).toPromise().catch(() => {
      throw new Error('Chưa đổi được vị trí thành địa chỉ. Bạn thử lại hoặc nhập địa chỉ thủ công nhé.');
    });
    const place = data?.features?.[0]?.properties;
    if (!place || place.countrycode?.toUpperCase() !== 'VN') {
      throw new Error('Chưa tìm được địa chỉ giao hàng tại Việt Nam ở vị trí này. Bạn nhập địa chỉ thủ công nhé.');
    }
    // Use local names regardless of the browser's preferred language (e.g. Hanoï).
    const provinces = this.provinceCache || [];
    const areaName = (name?: string): string | undefined => {
      if (!name) return undefined;
      const key = (value: string) => value.normalize('NFD').replace(/[\u0300-\u036f]/g, '')
        .replace(/[đĐ]/g, 'd').toLowerCase().replace(/\b(thanh pho|tinh|city|province)\b/g, '').replace(/[^a-z0-9]/g, '');
      const canonical = provinces.find(province => key(province.name) === key(name));
      return canonical ? canonical.name.replace(/^(Thành phố|Tỉnh)\s+/i, '') : name.normalize('NFC');
    };
    // A nearby POI's house number is not evidence of the user's own building.
    const parts = [place.street || place.name, place.locality, place.district, place.city || place.county, place.state]
      .map((part, index) => index === 0 ? part?.normalize('NFC') : areaName(part))
      .filter((part): part is string => !!part?.trim());
    const street = [...new Set(parts)].join(', ');
    if (!street || street.length > 255) throw new Error('Đã nhận vị trí nhưng chưa tìm được địa chỉ phù hợp. Bạn nhập địa chỉ nhé.');
    return { street, accuracy: Math.max(1, Math.round(accuracy || 1)) };
  }
}
