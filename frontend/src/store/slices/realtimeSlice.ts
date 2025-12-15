import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import { RealTimeData, InterfaceStats } from '../../types';
import { 
  getRealTimeData, 
  getErrorRateRanking, 
  getCircuitBreakerList, 
  getNormalInterfaceList,
  resetCircuitBreaker
} from '../../services/realtime';

interface RealtimeState {
  data: RealTimeData | null;
  errorRateRanking: InterfaceStats[];
  circuitBreakerList: InterfaceStats[];
  normalInterfaceList: InterfaceStats[];
  loading: boolean;
  error: string | null;
  lastUpdated: number;
}

const initialState: RealtimeState = {
  data: null,
  errorRateRanking: [],
  circuitBreakerList: [],
  normalInterfaceList: [],
  loading: false,
  error: null,
  lastUpdated: 0
};

// 异步action - 获取实时数据
export const fetchRealTimeData = createAsyncThunk(
  'realtime/fetchRealTimeData',
  async (_, { rejectWithValue }) => {
    try {
      return await getRealTimeData();
    } catch (error: any) {
      return rejectWithValue(error.message || '获取实时数据失败');
    }
  }
);

// 异步action - 获取错误率排行榜
export const fetchErrorRateRanking = createAsyncThunk(
  'realtime/fetchErrorRateRanking',
  async (_, { rejectWithValue }) => {
    try {
      return await getErrorRateRanking();
    } catch (error: any) {
      return rejectWithValue(error.message || '获取错误率排行榜失败');
    }
  }
);

// 异步action - 获取熔断接口列表
export const fetchCircuitBreakerList = createAsyncThunk(
  'realtime/fetchCircuitBreakerList',
  async (_, { rejectWithValue }) => {
    try {
      return await getCircuitBreakerList();
    } catch (error: any) {
      return rejectWithValue(error.message || '获取熔断接口列表失败');
    }
  }
);

// 异步action - 获取正常接口列表
export const fetchNormalInterfaceList = createAsyncThunk(
  'realtime/fetchNormalInterfaceList',
  async (_, { rejectWithValue }) => {
    try {
      return await getNormalInterfaceList();
    } catch (error: any) {
      return rejectWithValue(error.message || '获取正常接口列表失败');
    }
  }
);

// 异步action - 重置熔断状态
export const resetCircuitBreakerStatus = createAsyncThunk(
  'realtime/resetCircuitBreaker',
  async (interfaceId: string, { rejectWithValue }) => {
    try {
      await resetCircuitBreaker(interfaceId);
      return interfaceId;
    } catch (error: any) {
      return rejectWithValue(error.message || '重置熔断状态失败');
    }
  }
);

const realtimeSlice = createSlice({
  name: 'realtime',
  initialState,
  reducers: {
    clearError: (state) => {
      state.error = null;
    },
    startLoading: (state) => {
      state.loading = true;
      state.error = null;
    },
    stopLoading: (state) => {
      state.loading = false;
    }
  },
  extraReducers: (builder) => {
    builder
      // 处理fetchRealTimeData
      .addCase(fetchRealTimeData.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(fetchRealTimeData.fulfilled, (state, action: PayloadAction<RealTimeData>) => {
        state.loading = false;
        state.data = action.payload;
        state.errorRateRanking = action.payload.errorRateRanking;
        state.circuitBreakerList = action.payload.circuitBreakerList;
        state.normalInterfaceList = action.payload.normalInterfaceList;
        state.lastUpdated = Date.now();
      })
      .addCase(fetchRealTimeData.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload as string;
      })
      
      // 处理fetchErrorRateRanking
      .addCase(fetchErrorRateRanking.fulfilled, (state, action: PayloadAction<InterfaceStats[]>) => {
        state.errorRateRanking = action.payload;
        if (state.data) {
          state.data.errorRateRanking = action.payload;
        }
      })
      
      // 处理fetchCircuitBreakerList
      .addCase(fetchCircuitBreakerList.fulfilled, (state, action: PayloadAction<InterfaceStats[]>) => {
        state.circuitBreakerList = action.payload;
        if (state.data) {
          state.data.circuitBreakerList = action.payload;
        }
      })
      
      // 处理fetchNormalInterfaceList
      .addCase(fetchNormalInterfaceList.fulfilled, (state, action: PayloadAction<InterfaceStats[]>) => {
        state.normalInterfaceList = action.payload;
        if (state.data) {
          state.data.normalInterfaceList = action.payload;
        }
      })
      
      // 处理resetCircuitBreakerStatus
      .addCase(resetCircuitBreakerStatus.fulfilled, (state, action: PayloadAction<string>) => {
        const interfaceId = action.payload;
        state.circuitBreakerList = state.circuitBreakerList.filter(item => item.interfaceId !== interfaceId);
        if (state.data) {
          state.data.circuitBreakerList = state.data.circuitBreakerList.filter(item => item.interfaceId !== interfaceId);
        }
      });
  }
});

export const { clearError, startLoading, stopLoading } = realtimeSlice.actions;
export default realtimeSlice.reducer;