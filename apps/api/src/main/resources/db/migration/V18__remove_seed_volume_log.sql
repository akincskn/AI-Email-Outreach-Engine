-- V14 seed'i volume_log'a (CURRENT_DATE, 0, 0) ekliyordu. Bu satır, kurulumun
-- yapıldığı günü kalıcı olarak cap=0'a kilitliyordu: VolumeLimiterService.getOrCreateToday()
-- mevcut satırı bulup cap'i asla yeniden hesaplamadığı için o gün hiç mail gönderilemiyordu.
--
-- Doğru davranış: satır yoksa servis runtime'da computeCap() (warming takvimi) ile
-- oluşturur — yani seed satırı gereksiz. V14 zaten uygulandığından (Flyway checksum)
-- düzenlenemez; bu yüzden kötü seed satırını burada siliyoruz.
--
-- Yalnızca hiç kullanılmamış (sent_count=0) ve cap=0 olan seed satırını hedefler;
-- gerçek warming/gönderim verisini etkilemez.

DELETE FROM volume_log WHERE sent_count = 0 AND daily_cap = 0;
