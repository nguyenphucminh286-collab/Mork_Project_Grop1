package vn.edu.fpt.savora.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.savora.entity.Voucher;
import vn.edu.fpt.savora.enums.VoucherStatus;

import java.util.List;
import java.util.Optional;

@Repository
public interface VoucherRepository extends JpaRepository<Voucher, Integer> {

    List<Voucher> findByStatusOrderByDiscountPercentDesc(VoucherStatus status);

    Optional<Voucher> findByVoucherCodeIgnoreCase(String voucherCode);
}
