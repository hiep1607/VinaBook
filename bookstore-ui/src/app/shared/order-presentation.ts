import { ChatMessage } from '../services/chatbot.service';

export function orderStatusClass(code: number): string {
  return ['pending', 'shipping', 'completed', 'cancelled'][code] || 'unknown';
}
export function paymentLabel(order: {paymentMethod?: string; paymentStatus?: string}): string {
  if (order.paymentStatus === 'PAID') return 'Đã thanh toán';
  if (order.paymentStatus === 'FAILED') return 'Thanh toán chưa hoàn tất';
  return order.paymentMethod === 'COD' ? 'Thanh toán khi nhận hàng' : 'Chờ thanh toán';
}

/** Only hydrate explicit IDs in a completed server order lookup, never a user prompt. */
export function chatOrderIds(message: ChatMessage): number[] {
  if (message.from !== 'bot' || message.streaming || message.intent !== 'ORDER_SUPPORT'
      || !['getMyOrders', 'getOrderDetail'].includes(message.execution?.tool || '')
      || message.actions?.some(action => action.type === 'LOGIN')) return [];
  const ids: number[] = [];
  const pattern = /(?:^|\n)\s*(?:Đơn\s+|[•*-]\s*)#(\d+)\b/g;
  let match: RegExpExecArray | null;
  while ((match = pattern.exec(message.text)) && ids.length < 3) {
    const id = Number(match[1]);
    if (Number.isSafeInteger(id) && id > 0 && !ids.includes(id)) ids.push(id);
  }
  return ids;
}
