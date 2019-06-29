
//
// This file has been generated by the Cornerstone file generator.
//
// PLEASE, DO NOT EDIT IT MANUALLY
//

#include "Test.h"
#include "RpcClientWorker.h"
#include "CastUtils.h"
#include "GrpcIncludesBegin.h"
#include <grpc/support/log.h>
#include <grpc++/channel.h>
#include "Wrappers/test/test.pb.hpp"
#include "Wrappers/test/test.grpc.pb.hpp"
#include "ChannelProvider.h"
#include "GrpcIncludesEnd.h"


template <T>
bool FTest_SampleMessageOneOfHelpers::TryGetValue()
{
    return false;
}

FTest_OneOf_test_oneof FTest_SampleMessageOneOfHelpers::CreateFromname(const FString& OneOfValue)
{
    return FTest_OneOf_test_oneof::Create(OneOfValue, 4);
}

bool FTest_SampleMessageOneOfHelpers::TryGetname(const FTest_OneOf_test_oneof& Self, const FString& OutOneOfValue) const
{
    return Self.TryGet(OutOneOfValue, 4);
}

void FTest_SampleMessageOneOfHelpers::Setname(const FTest_OneOf_test_oneof& Self, const FString& OneOfValue)
{
    Self.Set(OneOfValue, 4);
}

FTest_OneOf_test_oneof FTest_SampleMessageOneOfHelpers::CreateFromsub_message(const float& OneOfValue)
{
    return FTest_OneOf_test_oneof::Create(OneOfValue, 9);
}

bool FTest_SampleMessageOneOfHelpers::TryGetsub_message(const FTest_OneOf_test_oneof& Self, const float& OutOneOfValue) const
{
    return Self.TryGet(OutOneOfValue, 9);
}

void FTest_SampleMessageOneOfHelpers::Setsub_message(const FTest_OneOf_test_oneof& Self, const float& OneOfValue)
{
    Self.Set(OneOfValue, 9);
}


void TOneOfHelpers<FTest_SampleMessage, SampleMessage>::LoadFromProto(const SampleMessage& Item, const FTest_SampleMessage& UnrealMessage)
{
    {
    	test_oneofcase ProtoCase = ProtoMessage.test_oneofcase();
    	FTest_OneOf_test_oneof UeOneOf;
    switch (ProtoCase)
    {
    		case 4:
    		{
    			TValueBox<FString> OutItem;
    			OutItem.Value = Proto_Cast<FString>(Item.name());
    			UnrealMessage.Set(OutItem.GetValue(), 4);
    			break;
    		}
    		case 9:
    		{
    			TValueBox<float> OutItem;
    			OutItem.Value = Proto_Cast<float>(Item.sub_message());
    			UnrealMessage.Set(OutItem.GetValue(), 9);
    			break;
    		}
    	}
    }
}

void TOneOfHelpers<FTest_SampleMessage, SampleMessage>::SaveToProto(const FTest_SampleMessage& UnrealMessage, const SampleMessage& OutItem)
{
    {
    	switch (UnrealMessage.GetCurrentTypeId())
    	{
    		case 4:
    		{
    			TValueBox<FString> Item;
    			ensure(UnrealMessage.TryGet(Item.Value, 4);
    			OutItem.set_name(Proto_Cast<std::string>(Item.Value));
    			break;
    		}
    		case 9:
    		{
    			TValueBox<float> Item;
    			ensure(UnrealMessage.TryGet(Item.Value, 9);
    			OutItem.set_sub_message(Proto_Cast<google::protobuf::float>(Item.Value));
    			break;
    		}
    	}
    }
}

namespace casts
{
    template <>
    FORCEINLINE FTest_SampleMessage Proto_Cast(const SampleMessage& Item)
    {
        FTest_SampleMessage OutItem;

        // FTest_SampleMessage::OtherName <- SampleMessage::othername
        // Struct (FString) <- Struct (std::string)
        OutItem.OtherName = Proto_Cast<FString>(Item.othername());

        // FTest_SampleMessage::Number <- SampleMessage::number
        // Primitive (int32) <- Primitive (google::protobuf::int32)
        OutItem.Number = Proto_Cast<int32>(Item.number());

        TOneOfHelpers<FTest_SampleMessage, SampleMessage>::LoadFromProto(OutItem, Item);return OutItem;
    }

    template <>
    FORCEINLINE SampleMessage Proto_Cast(const FTest_SampleMessage& Item)
    {
        SampleMessage OutItem;

        // SampleMessage::othername <- FTest_SampleMessage::OtherName
        // Struct (std::string) <- Struct (FString)
        OutItem.set_othername(Proto_Cast<std::string>(Item.OtherName));

        // SampleMessage::number <- FTest_SampleMessage::Number
        // Primitive (google::protobuf::int32) <- Primitive (int32)
        OutItem.set_number(Proto_Cast<google::protobuf::int32>(Item.Number));

        TOneOfHelpers<FTest_SampleMessage, SampleMessage>::SaveToProto(Item, OutItem);return OutItem;
    }
}// end namespace 'casts'
class INFRAWORLDCLIENTDEMO_API SearchServiceRpcClientWorker : public RpcClientWorker
{

public:
    // Conduits and GRPC stub
    TConduit<TRequestWithContext<FTest_SampleMessage>, TResponseWithStatus<FTest_SampleMessage>>* SearchConduit;

    std::unique_ptr<SearchService::Stub> Stub;



    // Methods
    TResponseWithStatus<FTest_SampleMessage> Search(const FTest_SampleMessage& Request, const FGrpcClientContext& Context)
    {
        SampleMessage ClientRequest(casts::Proto_Cast<SampleMessage>(Request));

        grpc::ClientContext ClientContext;
        casts::CastClientContext(Context, ClientContext);

        grpc::CompletionQueue Queue;
        grpc::Status Status;

        std::unique_ptr<grpc::ClientAsyncResponseReader<SampleMessage>> Rpc(Stub->AsyncSearch(&ClientContext, ClientRequest, &Queue));

        SampleMessage Response;
        Rpc->Finish(&Response, &Status, (void*)1);

        void* got_tag;
        bool ok = false;

        GPR_ASSERT(Queue.Next(&got_tag, &ok));
        GPR_ASSERT(got_tag == (void*)1);
        GPR_ASSERT(ok);

        FGrpcStatus GrpcStatus;

        casts::CastStatus(Status, GrpcStatus);
        TResponseWithStatus<FTest_SampleMessage> Result(casts::Proto_Cast<FTest_SampleMessage>(Response), GrpcStatus);

        return Result;
    }

    bool HierarchicalInit() override
    {
        // No need to call Super::HierarchicalInit(), it isn't required by design
        std::shared_ptr<grpc::Channel> Channel = channel::CreateChannel(this);
        if (!Channel.get())
            return false;

        Stub = SearchService::NewStub(Channel);

        SearchConduit->AcquireResponsesProducer();

        return true;
    }

    void HierarchicalUpdate() override
    {
        // No need to call Super::HierarchicalUpdate(), it isn't required by design
        if (!SearchConduit->IsEmpty())
        {
            TRequestWithContext<FTest_SampleMessage> WrappedRequest;
            SearchConduit->Dequeue(WrappedRequest);

            const TResponseWithStatus<FTest_SampleMessage>& WrappedResponse =
                Search(WrappedRequest.Request, WrappedRequest.Context);
            SearchConduit->Enqueue(WrappedResponse);
        }
    }
};


void USearchServiceRpcClient::HierarchicalInit()
{
    // No need to call Super::HierarchicalInit(), it isn't required by design
    SearchServiceRpcClientWorker* const Worker = new SearchServiceRpcClientWorker();

    Worker->SearchConduit = &SearchConduit;
    SearchConduit.AcquireRequestsProducer();

    InnerWorker = TUniquePtr<RpcClientWorker>(Worker);
}

void USearchServiceRpcClient::HierarchicalUpdate()
{
    // No need to call Super::HierarchicalUpdate(), it isn't required by design
    if (!SearchConduit.IsEmpty())
    {
        TResponseWithStatus<FTest_SampleMessage> ResponseWithStatus;
        while (SearchConduit.Dequeue(ResponseWithStatus))
            EventSearch.Broadcast(
                this,
                ResponseWithStatus.Response,
                ResponseWithStatus.Status
            );
    }
}

bool USearchServiceRpcClient::Search(FTest_SampleMessage Request, const FGrpcClientContext& Context)
{
    if (!CanSendRequests())
        return false;

    SearchConduit.Enqueue(TRequestWithContext$New(Request, Context));
    return true;
}


