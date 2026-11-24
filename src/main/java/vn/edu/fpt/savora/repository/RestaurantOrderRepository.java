package vn.edu.fpt.savora.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.savora.entity.RestaurantOrder;
import vn.edu.fpt.savora.enums.OrderStatus;
import vn.edu.fpt.savora.enums.OrderType;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface RestaurantOrderRepository extends JpaRepository<RestaurantOrder, Integer> {

    Optional<RestaurantOrder> findFirstByDiningTable_TableIdAndOrderTypeAndStatusInOrderByCreatedAtDesc(
            Integer tableId,
            OrderType orderType,
            Collection<OrderStatus> statuses
    );
}
