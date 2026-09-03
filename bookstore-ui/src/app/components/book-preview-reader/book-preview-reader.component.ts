import {
  AfterViewInit,
  Component,
  ElementRef,
  EventEmitter,
  HostListener,
  Input,
  NgZone,
  OnChanges,
  OnDestroy,
  Output,
  SimpleChanges,
  ViewChild
} from '@angular/core';
import type * as ThreeTypes from 'three';
import { gsap } from 'gsap';
import { BookPreviewPage } from '../../models/book-preview';

type TurnDirection = 'next' | 'previous';

@Component({
  selector: 'app-book-preview-reader',
  templateUrl: './book-preview-reader.component.html',
  styleUrls: ['./book-preview-reader.component.scss']
})
export class BookPreviewReaderComponent implements AfterViewInit, OnChanges, OnDestroy {
  private readonly pageWidth = 2.35;
  private readonly pageHeight = 3.25;
  private readonly pageSegments = 36;
  private readonly restingBookRotationX = -0.19;
  private readonly restingBookY = 0.18;

  @Input() title = 'Bản đọc thử';
  @Input() pages: BookPreviewPage[] = [];
  @Output() closed = new EventEmitter<void>();
  @ViewChild('canvasHost', { static: true }) canvasHost!: ElementRef<HTMLDivElement>;

  currentIndex = 0;
  loading = true;
  fallback = false;
  turning = false;
  dragging = false;
  hintVisible = true;

  private three?: typeof import('three');
  private scene?: ThreeTypes.Scene;
  private camera?: ThreeTypes.PerspectiveCamera;
  private renderer?: ThreeTypes.WebGLRenderer;
  private bookGroup?: ThreeTypes.Group;
  private leftPageMaterial?: ThreeTypes.MeshStandardMaterial;
  private rightPageMaterial?: ThreeTypes.MeshStandardMaterial;
  private turnFrontMaterial?: ThreeTypes.MeshStandardMaterial;
  private turnBackMaterial?: ThreeTypes.MeshStandardMaterial;
  private turnFrontGeometry?: ThreeTypes.PlaneGeometry;
  private turnBackGeometry?: ThreeTypes.PlaneGeometry;
  private turnGroup?: ThreeTypes.Group;
  private turnBasePositions?: Float32Array;
  private turnBaseUvs?: Float32Array;
  private blankTexture?: ThreeTypes.Texture;
  private pageTextures: ThreeTypes.Texture[] = [];
  private resizeObserver?: ResizeObserver;
  private resizeListening = false;
  private destroyed = false;
  private initialized = false;
  private textureVersion = 0;
  private turnTween?: gsap.core.Timeline;
  private entranceTween?: gsap.core.Timeline;
  private readonly motionMedia = gsap.matchMedia();
  private reducedMotion = false;
  private turnDirection: TurnDirection = 'next';
  private turnProgress = 0;
  private dragStartX = 0;
  private dragMoved = false;
  private dragPointerId?: number;

  private readonly onWindowResize = (): void => this.resizeRenderer();

  constructor(private readonly zone: NgZone) {
    this.motionMedia.add('(prefers-reduced-motion: reduce)', () => {
      this.reducedMotion = true;
      return () => {
        this.reducedMotion = false;
      };
    });
  }

  ngAfterViewInit(): void {
    this.zone.runOutsideAngular(() => this.initializeScene());
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (!changes.pages) {
      return;
    }
    this.currentIndex = 0;
    this.resetTurnState();
    if (this.initialized) {
      this.loadPageTextures();
    }
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    this.turnTween?.kill();
    this.entranceTween?.kill();
    this.motionMedia.revert();
    this.resizeObserver?.disconnect();
    if (this.resizeListening) {
      window.removeEventListener('resize', this.onWindowResize);
    }
    this.disposeScene();
  }

  get leftPage(): BookPreviewPage | undefined {
    return this.pages[this.currentIndex];
  }

  get rightPage(): BookPreviewPage | undefined {
    return this.pages[this.currentIndex + 1];
  }

  get canPrevious(): boolean {
    return this.currentIndex >= 2 && !this.turning && !this.loading;
  }

  get canNext(): boolean {
    return this.currentIndex + 1 < this.pages.length && !this.turning && !this.loading;
  }

  get pageLabel(): string {
    if (!this.pages.length) {
      return '0 / 0';
    }
    const first = this.currentIndex + 1;
    const last = Math.min(this.currentIndex + 2, this.pages.length);
    return first === last ? `${first} / ${this.pages.length}` : `${first}-${last} / ${this.pages.length}`;
  }

  close(): void {
    this.turnTween?.kill();
    this.closed.emit();
  }

  onBackdropClick(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.close();
    }
  }

  previous(): void {
    if (this.canPrevious) {
      this.startAutomaticTurn('previous');
    }
  }

  next(): void {
    if (this.canNext) {
      this.startAutomaticTurn('next');
    }
  }

  onPointerDown(event: PointerEvent): void {
    if (this.turning || this.loading || (event.pointerType === 'mouse' && event.button !== 0)) {
      return;
    }
    const host = event.currentTarget as HTMLElement;
    const bounds = host.getBoundingClientRect();
    const direction: TurnDirection = event.clientX >= bounds.left + bounds.width / 2 ? 'next' : 'previous';
    if ((direction === 'next' && !this.canNext) || (direction === 'previous' && !this.canPrevious)) {
      return;
    }
    if (!this.prepareTurn(direction)) {
      return;
    }
    if (this.reducedMotion) {
      this.finishTurn(direction);
      return;
    }
    this.dragging = true;
    this.dragStartX = event.clientX;
    this.dragMoved = false;
    this.dragPointerId = event.pointerId;
    host.setPointerCapture(event.pointerId);
  }

  onPointerMove(event: PointerEvent): void {
    if (!this.dragging || event.pointerId !== this.dragPointerId) {
      return;
    }
    const bounds = (event.currentTarget as HTMLElement).getBoundingClientRect();
    const halfWidth = Math.max(1, bounds.width / 2);
    const distance = this.turnDirection === 'next'
      ? this.dragStartX - event.clientX
      : event.clientX - this.dragStartX;
    this.dragMoved = this.dragMoved || Math.abs(event.clientX - this.dragStartX) > 5;
    if (this.dragMoved) {
      event.preventDefault();
    }
    this.setTurnProgress(Math.max(0, Math.min(1, distance / halfWidth)));
  }

  onPointerUp(event: PointerEvent): void {
    if (!this.dragging || event.pointerId !== this.dragPointerId) {
      return;
    }
    this.finishPointerTurn(event, event.currentTarget as HTMLElement);
  }

  @HostListener('document:pointerup', ['$event'])
  @HostListener('document:pointercancel', ['$event'])
  onDocumentPointerEnd(event: PointerEvent): void {
    if (!this.dragging || event.pointerId !== this.dragPointerId) {
      return;
    }
    this.finishPointerTurn(event, this.canvasHost.nativeElement);
  }

  private finishPointerTurn(event: PointerEvent, host: HTMLElement): void {
    if (host.hasPointerCapture(event.pointerId)) {
      host.releasePointerCapture(event.pointerId);
    }
    this.dragging = false;
    this.dragPointerId = undefined;
    if (!this.dragMoved || this.turnProgress >= 0.27) {
      this.animateTurnTo(1, true);
    } else {
      this.animateTurnTo(0, false);
    }
  }

  @HostListener('document:keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      event.preventDefault();
      this.close();
    } else if (event.key === 'ArrowRight') {
      event.preventDefault();
      this.next();
    } else if (event.key === 'ArrowLeft') {
      event.preventDefault();
      this.previous();
    }
  }

  private async initializeScene(): Promise<void> {
    if (this.destroyed || !this.isWebGlAvailable()) {
      this.showFallback();
      return;
    }
    try {
      const THREE = await import('three');
      if (this.destroyed) {
        return;
      }
      this.three = THREE;
      this.scene = new THREE.Scene();
      this.camera = new THREE.PerspectiveCamera(35, 1, 0.1, 50);
      this.camera.position.set(0, 0.26, 7.45);
      this.camera.lookAt(0, 0.02, 0);

      this.renderer = new THREE.WebGLRenderer({ alpha: true, antialias: true, powerPreference: 'high-performance' });
      this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
      this.renderer.setClearColor(0x000000, 0);
      this.renderer.outputEncoding = THREE.sRGBEncoding;
      this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
      this.renderer.toneMappingExposure = 1.02;
      this.renderer.shadowMap.enabled = true;
      this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;
      this.renderer.domElement.setAttribute('aria-hidden', 'true');
      this.renderer.domElement.style.display = 'block';
      this.renderer.domElement.style.width = '100%';
      this.renderer.domElement.style.height = '100%';
      this.canvasHost.nativeElement.appendChild(this.renderer.domElement);

      this.buildLights();
      this.buildBook();
      this.setupResizeObserver();
      this.initialized = true;
      this.resizeRenderer();
      await this.loadPageTextures();
    } catch {
      this.showFallback();
    }
  }

  private buildLights(): void {
    const THREE = this.three;
    if (!THREE || !this.scene) {
      return;
    }
    this.scene.add(new THREE.HemisphereLight(0xf3f1e8, 0x202b28, 0.72));
    this.scene.add(new THREE.AmbientLight(0xe9eee9, 0.2));
    const key = new THREE.DirectionalLight(0xfff4df, 1.08);
    key.position.set(2.6, 4.8, 6.8);
    key.castShadow = true;
    key.shadow.mapSize.set(1024, 1024);
    key.shadow.camera.left = -5;
    key.shadow.camera.right = 5;
    key.shadow.camera.top = 4;
    key.shadow.camera.bottom = -4;
    this.scene.add(key);
    const rim = new THREE.DirectionalLight(0xb8c8bf, 0.3);
    rim.position.set(-4, 1.5, 3.2);
    this.scene.add(rim);
  }

  private buildBook(): void {
    const THREE = this.three;
    if (!THREE || !this.scene) {
      return;
    }
    const book = new THREE.Group();
    book.rotation.x = this.restingBookRotationX;
    book.position.y = this.restingBookY;
    this.bookGroup = book;
    this.scene.add(book);

    const coverMaterial = new THREE.MeshStandardMaterial({ color: 0x284c43, roughness: 0.72, metalness: 0.02 });
    const pageEdgeMaterials = [0xc7bdad, 0xd6ccbd, 0xe5ddcf].map(color =>
      new THREE.MeshStandardMaterial({ color, roughness: 0.96 })
    );
    const coverDepth = 0.12;
    const coverWidth = this.pageWidth + 0.15;
    const coverHeight = this.pageHeight + 0.12;

    const leftCover = new THREE.Mesh(new THREE.BoxGeometry(coverWidth, coverHeight, coverDepth), coverMaterial);
    leftCover.position.set(-this.pageWidth / 2 - 0.045, -0.025, -0.145);
    leftCover.rotation.y = -0.012;
    leftCover.receiveShadow = true;
    book.add(leftCover);
    const rightCover = new THREE.Mesh(new THREE.BoxGeometry(coverWidth, coverHeight, coverDepth), coverMaterial);
    rightCover.position.set(this.pageWidth / 2 + 0.045, -0.025, -0.145);
    rightCover.rotation.y = 0.012;
    rightCover.receiveShadow = true;
    book.add(rightCover);
    const spine = new THREE.Mesh(new THREE.CylinderGeometry(0.09, 0.09, coverHeight - 0.07, 24), coverMaterial);
    spine.position.set(0, -0.025, -0.15);
    spine.scale.z = 0.78;
    spine.receiveShadow = true;
    book.add(spine);

    for (let layer = 0; layer < 7; layer++) {
      const depth = 0.02;
      const z = -0.095 + layer * 0.014;
      const pageEdgeMaterial = pageEdgeMaterials[layer % pageEdgeMaterials.length];
      const leftStack = new THREE.Mesh(new THREE.BoxGeometry(this.pageWidth - 0.035, this.pageHeight - 0.035, depth), pageEdgeMaterial);
      leftStack.position.set(-this.pageWidth / 2 - 0.018, 0, z);
      leftStack.rotation.y = -0.006;
      leftStack.receiveShadow = true;
      book.add(leftStack);
      const rightStack = new THREE.Mesh(new THREE.BoxGeometry(this.pageWidth - 0.035, this.pageHeight - 0.035, depth), pageEdgeMaterial);
      rightStack.position.set(this.pageWidth / 2 + 0.018, 0, z);
      rightStack.rotation.y = 0.006;
      rightStack.receiveShadow = true;
      book.add(rightStack);
    }

    this.blankTexture = this.createPaperTexture();
    this.leftPageMaterial = new THREE.MeshStandardMaterial({ map: this.blankTexture, color: 0xfffbf2, roughness: 0.94, side: THREE.FrontSide });
    this.rightPageMaterial = new THREE.MeshStandardMaterial({ map: this.blankTexture, color: 0xfffbf2, roughness: 0.94, side: THREE.FrontSide });
    const leftPage = new THREE.Mesh(this.createRestingPageGeometry('left'), this.leftPageMaterial);
    leftPage.position.set(-this.pageWidth / 2, 0, 0.035);
    leftPage.receiveShadow = true;
    book.add(leftPage);
    const rightPage = new THREE.Mesh(this.createRestingPageGeometry('right'), this.rightPageMaterial);
    rightPage.position.set(this.pageWidth / 2, 0, 0.035);
    rightPage.receiveShadow = true;
    book.add(rightPage);

    const gutterMaterial = new THREE.MeshBasicMaterial({ color: 0x6a6258, transparent: true, opacity: 0.15, depthWrite: false });
    const gutter = new THREE.Mesh(new THREE.PlaneGeometry(0.035, this.pageHeight - 0.045), gutterMaterial);
    gutter.position.set(0, 0, 0.064);
    gutter.renderOrder = 8;
    book.add(gutter);

    this.turnFrontGeometry = new THREE.PlaneGeometry(this.pageWidth, this.pageHeight, this.pageSegments, 8);
    this.turnFrontGeometry.translate(this.pageWidth / 2, 0, 0);
    this.turnBackGeometry = this.turnFrontGeometry.clone();
    const positionAttribute = this.turnFrontGeometry.getAttribute('position') as ThreeTypes.BufferAttribute;
    const uvAttribute = this.turnFrontGeometry.getAttribute('uv') as ThreeTypes.BufferAttribute;
    this.turnBasePositions = new Float32Array(positionAttribute.array as ArrayLike<number>);
    this.turnBaseUvs = new Float32Array(uvAttribute.array as ArrayLike<number>);
    this.turnFrontMaterial = new THREE.MeshStandardMaterial({ map: this.blankTexture, roughness: 0.82, side: THREE.FrontSide });
    this.turnBackMaterial = new THREE.MeshStandardMaterial({ map: this.blankTexture, roughness: 0.82, side: THREE.BackSide });
    const frontMesh = new THREE.Mesh(this.turnFrontGeometry, this.turnFrontMaterial);
    const backMesh = new THREE.Mesh(this.turnBackGeometry, this.turnBackMaterial);
    frontMesh.castShadow = true;
    backMesh.castShadow = true;
    const turnGroup = new THREE.Group();
    turnGroup.position.z = 0.1;
    turnGroup.add(frontMesh, backMesh);
    turnGroup.visible = false;
    turnGroup.renderOrder = 12;
    this.turnGroup = turnGroup;
    book.add(turnGroup);

    this.addContactShadow(book);
  }

  private createRestingPageGeometry(side: 'left' | 'right'): ThreeTypes.PlaneGeometry {
    const THREE = this.three!;
    const geometry = new THREE.PlaneGeometry(this.pageWidth, this.pageHeight, 28, 10);
    const positions = geometry.getAttribute('position') as ThreeTypes.BufferAttribute;
    for (let index = 0; index < positions.count; index++) {
      const x = positions.getX(index);
      const u = x / this.pageWidth + 0.5;
      const distanceFromGutter = side === 'left' ? 1 - u : u;
      const boundLift = 0.052 * Math.exp(-Math.pow((distanceFromGutter - 0.15) / 0.16, 2));
      const outerSettle = 0.008 * distanceFromGutter;
      positions.setZ(index, boundLift - outerSettle);
    }
    positions.needsUpdate = true;
    geometry.computeVertexNormals();
    return geometry;
  }

  private addContactShadow(book: ThreeTypes.Group): void {
    const THREE = this.three;
    if (!THREE) {
      return;
    }
    const canvas = document.createElement('canvas');
    canvas.width = 512;
    canvas.height = 256;
    const context = canvas.getContext('2d');
    if (!context) {
      return;
    }
    const gradient = context.createRadialGradient(256, 128, 18, 256, 128, 246);
    gradient.addColorStop(0, 'rgba(18, 20, 18, .42)');
    gradient.addColorStop(0.55, 'rgba(18, 20, 18, .19)');
    gradient.addColorStop(1, 'rgba(18, 20, 18, 0)');
    context.fillStyle = gradient;
    context.fillRect(0, 0, canvas.width, canvas.height);
    const texture = new THREE.CanvasTexture(canvas);
    const shadow = new THREE.Mesh(
      new THREE.PlaneGeometry(this.pageWidth * 2.45, this.pageHeight * 1.25),
      new THREE.MeshBasicMaterial({ map: texture, transparent: true, depthWrite: false, opacity: 0.72 })
    );
    shadow.position.set(0, -0.18, -0.29);
    shadow.renderOrder = -2;
    book.add(shadow);
  }

  private async loadPageTextures(): Promise<void> {
    const THREE = this.three;
    if (!THREE) {
      return;
    }
    const version = ++this.textureVersion;
    this.zone.run(() => this.loading = true);
    this.disposePageTextures();
    const loader = new THREE.TextureLoader();
    loader.setCrossOrigin('anonymous');
    const textures = await Promise.all(this.pages.map((page, index) => this.loadTexture(loader, page.imageUrl, index)));
    if (this.destroyed || version !== this.textureVersion) {
      textures.forEach(texture => texture.dispose());
      return;
    }
    this.pageTextures = textures;
    this.applySpreadTextures(this.currentIndex);
    this.zone.run(() => {
      this.loading = false;
      this.fallback = false;
    });
    this.playEntrance();
  }

  private playEntrance(): void {
    if (!this.bookGroup) {
      this.renderScene();
      return;
    }
    this.entranceTween?.kill();
    this.bookGroup.rotation.x = this.reducedMotion ? this.restingBookRotationX : -0.19;
    this.bookGroup.position.y = this.reducedMotion ? this.restingBookY : this.restingBookY - 0.12;
    if (this.reducedMotion) {
      this.renderScene();
      return;
    }
    this.entranceTween = gsap.timeline({
      defaults: { duration: 0.62, ease: 'power2.out' },
      onUpdate: () => this.renderScene(),
      onComplete: () => {
        this.entranceTween = undefined;
      }
    });
    this.entranceTween
      .to(this.bookGroup.rotation, { x: this.restingBookRotationX }, 0)
      .to(this.bookGroup.position, { y: this.restingBookY }, 0);
  }

  private loadTexture(loader: ThreeTypes.TextureLoader, source: string, pageIndex: number): Promise<ThreeTypes.Texture> {
    return new Promise(resolve => {
      loader.load(source, texture => {
        this.prepareTexture(texture);
        resolve(texture);
      }, undefined, () => resolve(this.createFallbackPageTexture(pageIndex)));
    });
  }

  private prepareTexture(texture: ThreeTypes.Texture): void {
    const THREE = this.three;
    if (!THREE) {
      return;
    }
    texture.encoding = THREE.sRGBEncoding;
    texture.anisotropy = Math.min(8, this.renderer?.capabilities.getMaxAnisotropy() || 1);
    texture.needsUpdate = true;
  }

  private createPaperTexture(): ThreeTypes.Texture {
    const THREE = this.three!;
    const canvas = document.createElement('canvas');
    canvas.width = 96;
    canvas.height = 128;
    const context = canvas.getContext('2d')!;
    context.fillStyle = '#f4eddc';
    context.fillRect(0, 0, canvas.width, canvas.height);
    for (let y = 0; y < canvas.height; y += 4) {
      context.fillStyle = y % 8 === 0 ? 'rgba(119, 92, 62, .018)' : 'rgba(255, 255, 255, .025)';
      context.fillRect(0, y, canvas.width, 1);
    }
    const texture = new THREE.CanvasTexture(canvas);
    this.prepareTexture(texture);
    return texture;
  }

  private createFallbackPageTexture(pageIndex: number): ThreeTypes.Texture {
    const THREE = this.three!;
    const canvas = document.createElement('canvas');
    canvas.width = 900;
    canvas.height = 1200;
    const context = canvas.getContext('2d')!;
    context.fillStyle = '#f7f0df';
    context.fillRect(0, 0, canvas.width, canvas.height);
    context.fillStyle = '#173f35';
    context.textAlign = 'center';
    context.font = '700 34px Georgia';
    context.fillText(this.title, canvas.width / 2, 120);
    context.fillStyle = '#8b7258';
    context.font = '18px Georgia';
    context.fillText(`TRANG ĐỌC THỬ ${pageIndex + 1}`, canvas.width / 2, 168);
    context.textAlign = 'left';
    context.fillStyle = '#4c4438';
    context.font = '25px Georgia';
    const lines = [
      'Nội dung trang đang được chuẩn bị.',
      'Vui lòng thử lại sau hoặc liên hệ nhà sách',
      'để xem bản đọc thử đầy đủ của đầu sách này.'
    ];
    lines.forEach((line, index) => context.fillText(line, 105, 330 + index * 48));
    context.fillStyle = 'rgba(76, 68, 56, .22)';
    for (let row = 0; row < 12; row++) {
      context.fillRect(105, 520 + row * 42, 690 - (row % 3) * 62, 3);
    }
    const texture = new THREE.CanvasTexture(canvas);
    this.prepareTexture(texture);
    return texture;
  }

  private startAutomaticTurn(direction: TurnDirection): void {
    if (this.fallback) {
      const nextIndex = direction === 'next' ? this.currentIndex + 2 : this.currentIndex - 2;
      this.currentIndex = Math.max(0, Math.min(nextIndex, Math.max(0, this.pages.length - 1)));
      if (this.currentIndex % 2 !== 0) {
        this.currentIndex -= 1;
      }
      this.hintVisible = false;
      return;
    }
    if (!this.prepareTurn(direction)) {
      return;
    }
    if (this.reducedMotion) {
      this.finishTurn(direction);
      return;
    }
    this.animateTurnTo(1, true);
  }

  private prepareTurn(direction: TurnDirection): boolean {
    if (!this.turnGroup || !this.turnFrontMaterial || !this.turnBackMaterial) {
      return false;
    }
    this.turnTween?.kill();
    this.turnDirection = direction;
    this.turning = true;
    this.hintVisible = false;
    const frontIndex = direction === 'next' ? this.currentIndex + 1 : this.currentIndex;
    const backIndex = direction === 'next' ? this.currentIndex + 2 : this.currentIndex - 1;
    this.setMaterialTexture(this.turnFrontMaterial, this.textureAt(frontIndex));
    this.setMaterialTexture(this.turnBackMaterial, this.textureAt(backIndex));
    this.configureTurnUvs(direction);
    if (direction === 'next') {
      this.setMaterialTexture(this.leftPageMaterial, this.textureAt(this.currentIndex));
      this.setMaterialTexture(this.rightPageMaterial, this.textureAt(this.currentIndex + 3));
    } else {
      this.setMaterialTexture(this.leftPageMaterial, this.textureAt(this.currentIndex - 2));
      this.setMaterialTexture(this.rightPageMaterial, this.textureAt(this.currentIndex + 1));
    }
    this.turnGroup.visible = true;
    this.setTurnProgress(0);
    return true;
  }

  private animateTurnTo(target: 0 | 1, completeTurn: boolean): void {
    const state = { value: this.turnProgress };
    this.turnTween?.kill();
    this.turnTween = gsap.timeline({
      defaults: { ease: target === 1 ? 'power2.inOut' : 'power2.out' },
      onComplete: () => {
        if (completeTurn) {
          this.finishTurn(this.turnDirection);
        } else {
          this.zone.run(() => {
            this.applySpreadTextures(this.currentIndex);
            this.resetTurnState();
          });
          this.renderScene();
        }
      }
    });
    this.turnTween.to(state, {
      value: target,
      duration: Math.max(0.24, 0.82 * Math.abs(target - this.turnProgress)),
      onUpdate: () => this.setTurnProgress(state.value)
    }, 0);
    if (this.bookGroup) {
      this.turnTween
        .to(this.bookGroup.rotation, { x: this.restingBookRotationX - 0.018, duration: 0.34, ease: 'sine.inOut' }, 0)
        .to(this.bookGroup.rotation, { x: this.restingBookRotationX, duration: 0.38, ease: 'sine.out' }, 0.36);
    }
  }

  private setTurnProgress(progress: number): void {
    if (!this.turnFrontGeometry || !this.turnBackGeometry || !this.turnBasePositions) {
      return;
    }
    this.turnProgress = Math.max(0, Math.min(1, progress));
    const side = this.turnDirection === 'next' ? 1 : -1;
    const lift = Math.sin(Math.PI * this.turnProgress);
    const baseAngle = Math.PI * this.turnProgress;
    const front = this.turnFrontGeometry.attributes.position as ThreeTypes.BufferAttribute;
    const back = this.turnBackGeometry.attributes.position as ThreeTypes.BufferAttribute;
    const source = this.turnBasePositions;
    for (let index = 0; index < front.count; index++) {
      const sourceIndex = index * 3;
      const baseX = source[sourceIndex];
      const baseY = source[sourceIndex + 1];
      const u = Math.max(0, Math.min(1, baseX / this.pageWidth));
      const vertical = 1 - Math.pow((baseY / (this.pageHeight / 2)), 2);
      const curl = lift * Math.sin(Math.PI * u) * 0.22;
      const angle = baseAngle + (0.48 - u) * curl;
      const x = side * baseX * Math.cos(angle);
      const z = baseX * Math.sin(angle) + lift * Math.sin(Math.PI * u) * 0.17;
      const y = baseY - lift * Math.sin(Math.PI * u) * Math.max(0, vertical) * 0.028;
      front.setXYZ(index, x, y, z);
      back.setXYZ(index, x, y, z - 0.003);
    }
    front.needsUpdate = true;
    back.needsUpdate = true;
    this.turnFrontGeometry.computeVertexNormals();
    this.turnBackGeometry.computeVertexNormals();
    this.renderScene();
  }

  private configureTurnUvs(direction: TurnDirection): void {
    if (!this.turnFrontGeometry || !this.turnBackGeometry || !this.turnBaseUvs) {
      return;
    }
    const frontUvs = this.turnFrontGeometry.attributes.uv as ThreeTypes.BufferAttribute;
    const backUvs = this.turnBackGeometry.attributes.uv as ThreeTypes.BufferAttribute;
    for (let index = 0; index < frontUvs.count; index++) {
      const sourceIndex = index * 2;
      const u = this.turnBaseUvs[sourceIndex];
      const v = this.turnBaseUvs[sourceIndex + 1];
      frontUvs.setXY(index, direction === 'previous' ? 1 - u : u, v);
      backUvs.setXY(index, direction === 'next' ? 1 - u : u, v);
    }
    frontUvs.needsUpdate = true;
    backUvs.needsUpdate = true;
  }

  private finishTurn(direction: TurnDirection): void {
    this.zone.run(() => {
      this.currentIndex += direction === 'next' ? 2 : -2;
      this.currentIndex = Math.max(0, Math.min(this.currentIndex, Math.max(0, this.pages.length - 1)));
      this.applySpreadTextures(this.currentIndex);
      this.resetTurnState();
    });
    this.renderScene();
  }

  private resetTurnState(): void {
    this.turning = false;
    this.dragging = false;
    this.dragPointerId = undefined;
    this.turnProgress = 0;
    this.turnTween = undefined;
    if (this.turnGroup) {
      this.turnGroup.visible = false;
    }
  }

  private applySpreadTextures(index: number): void {
    this.setMaterialTexture(this.leftPageMaterial, this.textureAt(index));
    this.setMaterialTexture(this.rightPageMaterial, this.textureAt(index + 1));
  }

  private textureAt(index: number): ThreeTypes.Texture | undefined {
    return index >= 0 && index < this.pageTextures.length ? this.pageTextures[index] : this.blankTexture;
  }

  private setMaterialTexture(material: ThreeTypes.MeshStandardMaterial | undefined, texture: ThreeTypes.Texture | undefined): void {
    if (!material || !texture) {
      return;
    }
    material.map = texture;
    material.needsUpdate = true;
  }

  private setupResizeObserver(): void {
    if (typeof ResizeObserver !== 'undefined') {
      this.resizeObserver = new ResizeObserver(() => this.resizeRenderer());
      this.resizeObserver.observe(this.canvasHost.nativeElement);
    } else {
      window.addEventListener('resize', this.onWindowResize, { passive: true });
      this.resizeListening = true;
    }
  }

  private resizeRenderer(): void {
    if (!this.renderer || !this.camera || !this.canvasHost?.nativeElement) {
      return;
    }
    const bounds = this.canvasHost.nativeElement.getBoundingClientRect();
    const width = Math.max(1, Math.floor(bounds.width));
    const height = Math.max(1, Math.floor(bounds.height));
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    this.renderer.setSize(width, height, false);
    this.camera.aspect = width / height;
    this.camera.fov = this.camera.aspect < 0.78 ? 50 : 35;
    this.camera.position.z = this.camera.aspect < 0.78 ? 9.45 : 7.45;
    this.camera.updateProjectionMatrix();
    if (this.bookGroup) {
      const scale = this.camera.aspect < 0.78
        ? 0.74
        : this.camera.aspect < 1.2
          ? 0.98
          : Math.min(1.18, Math.max(1.02, width / 1080));
      this.bookGroup.scale.setScalar(scale);
    }
    this.renderScene();
  }

  private renderScene(): void {
    if (this.renderer && this.scene && this.camera) {
      this.renderer.render(this.scene, this.camera);
    }
  }

  private isWebGlAvailable(): boolean {
    try {
      const canvas = document.createElement('canvas');
      return Boolean(window.WebGLRenderingContext && (canvas.getContext('webgl') || canvas.getContext('experimental-webgl')));
    } catch {
      return false;
    }
  }

  private showFallback(): void {
    this.zone.run(() => {
      this.loading = false;
      this.fallback = true;
    });
  }

  private disposePageTextures(): void {
    this.pageTextures.forEach(texture => texture.dispose());
    this.pageTextures = [];
  }

  private disposeScene(): void {
    this.disposePageTextures();
    this.blankTexture?.dispose();
    this.scene?.traverse(object => {
      const mesh = object as ThreeTypes.Mesh;
      mesh.geometry?.dispose();
      const materials = Array.isArray(mesh.material) ? mesh.material : mesh.material ? [mesh.material] : [];
      materials.forEach(material => material.dispose());
    });
    this.renderer?.dispose();
    this.renderer?.domElement.remove();
    this.scene = undefined;
    this.renderer = undefined;
    this.camera = undefined;
  }
}
